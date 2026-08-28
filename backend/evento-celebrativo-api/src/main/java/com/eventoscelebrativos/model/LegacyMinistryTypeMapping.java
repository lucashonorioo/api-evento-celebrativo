package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tb_ministry_legacy_type_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tb_ministry_legacy_type_mapping_type",
                columnNames = "ministry_type"
        )
)
public class LegacyMinistryTypeMapping {

    @Id
    @Column(name = "ministry_id")
    private Long ministryId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;

    @Enumerated(EnumType.STRING)
    @Column(name = "ministry_type", nullable = false, length = 50)
    private MinistryType ministryType;

    protected LegacyMinistryTypeMapping() {
    }

    public LegacyMinistryTypeMapping(Ministry ministry, MinistryType ministryType) {
        if (ministry == null || ministry.getId() == null || ministry.getId() <= 0) {
            throw new IllegalArgumentException("Ministerio persistente e obrigatorio");
        }
        if (ministryType == null) {
            throw new IllegalArgumentException("Tipo ministerial legado e obrigatorio");
        }
        this.ministry = ministry;
        this.ministryId = ministry.getId();
        this.ministryType = ministryType;
    }

    public Long getMinistryId() {
        return ministryId;
    }

    public Ministry getMinistry() {
        return ministry;
    }

    public MinistryType getMinistryType() {
        return ministryType;
    }
}
