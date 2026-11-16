package com.stark.vms.vehicle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class VehicleQueryRepositoryImpl implements VehicleQueryRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Page<Vehicle> search(String search, VehicleStatus status, int page, int size,
			String sortBy, String sortDirection) {
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Vehicle> query = builder.createQuery(Vehicle.class);
		Root<Vehicle> root = query.from(Vehicle.class);
		root.fetch("currentDriver", JoinType.LEFT);
		query.select(root).where(predicates(builder, root, search, status));

		List<Order> orderBy = new ArrayList<>();
		if (sortBy != null) {
			Expression<?> selectedField = root.get(sortBy);
			orderBy.add("desc".equals(sortDirection) ? builder.desc(selectedField) : builder.asc(selectedField));
		} else if (search != null && !search.isBlank()) {
			String normalized = search.strip().toLowerCase(Locale.ROOT);
			String escaped = escapeLike(normalized);
			CriteriaBuilder.Case<Integer> rankCase = builder.selectCase();
			rankCase = rankCase.when(builder.equal(builder.lower(root.get("vehicleNumber")), normalized), 0);
			rankCase = rankCase.when(builder.like(builder.lower(root.get("vehicleNumber")), escaped + "%", '\\'), 1);
			rankCase = rankCase.when(builder.like(builder.lower(root.get("vehicleNumber")), "%" + escaped + "%", '\\'), 2);
			Expression<Integer> rank = rankCase.otherwise(3);
			orderBy.add(builder.asc(rank));
			orderBy.add(builder.desc(root.get("updatedAt")));
		} else {
			orderBy.add(builder.desc(root.get("updatedAt")));
		}
		orderBy.add(builder.asc(root.get("id")));
		query.orderBy(orderBy);

		PageRequest pageable = PageRequest.of(page, size);
		TypedQuery<Vehicle> select = entityManager.createQuery(query)
				.setFirstResult(Math.toIntExact(pageable.getOffset()))
				.setMaxResults(size);
		List<Vehicle> content = select.getResultList();

		CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
		Root<Vehicle> countRoot = countQuery.from(Vehicle.class);
		countQuery.select(builder.count(countRoot)).where(predicates(builder, countRoot, search, status));
		long total = entityManager.createQuery(countQuery).getSingleResult();
		return new PageImpl<>(content, pageable, total);
	}

	private static Predicate[] predicates(CriteriaBuilder builder, Root<Vehicle> root,
			String search, VehicleStatus status) {
		List<Predicate> predicates = new ArrayList<>();
		if (search != null && !search.isBlank()) {
			String escaped = "%" + escapeLike(search.strip().toLowerCase(Locale.ROOT)) + "%";
			predicates.add(builder.or(
					builder.like(builder.lower(root.get("vehicleNumber")), escaped, '\\'),
					builder.like(builder.lower(root.get("companyName")), escaped, '\\'),
					builder.like(builder.lower(root.get("vehicleType")), escaped, '\\'),
					builder.like(builder.lower(root.get("brand")), escaped, '\\'),
					builder.like(builder.lower(root.get("model")), escaped, '\\')));
		}
		if (status != null) {
			predicates.add(builder.equal(root.get("status"), status));
		}
		return predicates.toArray(Predicate[]::new);
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
