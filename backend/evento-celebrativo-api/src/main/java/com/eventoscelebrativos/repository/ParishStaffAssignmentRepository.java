package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.ParishStaffAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParishStaffAssignmentRepository extends JpaRepository<ParishStaffAssignment, Long> {

    Optional<ParishStaffAssignment> findByPersonIdAndResponsibility(Long personId, ParishResponsibilityType responsibility);

    /**
     * Bloqueia a linha existente de Person + responsibility para grant/revoke idempotente. Sobre um
     * par ainda inexistente nao ha o que bloquear (Optional vazio); a unicidade da primeira insercao
     * fica a cargo de {@code uk_tb_parish_staff_assignment_person_responsibility} combinada com o lock
     * de escopo mais amplo ja adquirido antes (Person para PARISH_SECRETARY, ParishProfile(id=1) para
     * PASTOR).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ParishStaffAssignment a WHERE a.person.id = :personId AND a.responsibility = :responsibility")
    Optional<ParishStaffAssignment> findByPersonIdAndResponsibilityForUpdate(
            @Param("personId") Long personId,
            @Param("responsibility") ParishResponsibilityType responsibility
    );

    List<ParishStaffAssignment> findByPersonIdOrderByResponsibilityAsc(Long personId);

    boolean existsByPersonIdAndActiveTrue(Long personId);

    List<ParishStaffAssignment> findByPersonIdAndActiveTrue(Long personId);

    boolean existsByPersonIdAndResponsibilityAndActiveTrue(Long personId, ParishResponsibilityType responsibility);

    /**
     * Leitura do PASTOR ativo atual. Deliberadamente sem lock proprio: usada apenas dentro do
     * bloco critico ja serializado pelo mutex {@code ParishProfile(id=1)} (ver
     * ParishStaffAssignmentServiceImpl), entao uma leitura simples e suficiente e consistente.
     */
    Optional<ParishStaffAssignment> findFirstByResponsibilityAndActiveTrue(ParishResponsibilityType responsibility);

    /**
     * Projecao da equipe paroquial ativa atual (PASTOR e PARISH_SECRETARY), com join implicito em
     * Person para evitar N+1. Filtra defensivamente assignment.active=TRUE e person.active=TRUE mesmo
     * que o invariante de dominio ja garanta isso, para nunca expor uma responsabilidade orfa de uma
     * Person inativa.
     */
    @Query("""
            SELECT a.person.id AS personId, a.person.name AS name
            FROM ParishStaffAssignment a
            WHERE a.responsibility = :responsibility
              AND a.active = TRUE
              AND a.person.active = TRUE
            ORDER BY a.person.name ASC, a.person.id ASC
            """)
    List<ParishStaffMemberProjection> findActiveMembersByResponsibility(
            @Param("responsibility") ParishResponsibilityType responsibility
    );

    interface ParishStaffMemberProjection {
        Long getPersonId();

        String getName();
    }
}
