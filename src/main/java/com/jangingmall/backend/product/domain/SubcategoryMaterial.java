package com.jangingmall.backend.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subcategory_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubcategoryMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id", nullable = false)
    private Subcategory subcategory;

    @Column(nullable = false, length = 50)
    private String name;

    public static SubcategoryMaterial of(Subcategory subcategory, String name) {
        SubcategoryMaterial material = new SubcategoryMaterial();
        material.subcategory = subcategory;
        material.name = name;
        return material;
    }
}
