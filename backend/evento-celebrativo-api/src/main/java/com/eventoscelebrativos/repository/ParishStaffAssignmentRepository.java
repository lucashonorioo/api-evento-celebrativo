package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.ParishStaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParishStaffAssignmentRepository extends JpaRepository<ParishStaffAssignment, Long> {

    /**
     * Sem lock proprio: grant/revoke serializam pelo lock de escopo mais amplo ja adquirido antes
     * desta consulta (Person para PARISH_SECRETARY, ParishProfile(id=1) + Person para PASTOR). Um
     * {@code SELECT ... FOR UPDATE} aqui seria redundante e, sobre uma linha ainda inexistente (par
     * Person+responsibility nunca concedido), arriscaria gap/next-key locking no InnoDB entre Persons
     * sem nenhuma relacao logica entre si (ex.: duas secretarias diferentes disputando um mutex que
     * nao deveriam compartilhar). A unicidade final fica a cargo de
     * {@code uk_tb_parish_staff_assignment_person_responsibility}.
     */
    Optional<ParishStaffAssignment> findByPersonIdAndResponsibility(Long personId, ParishResponsibilityType responsibility);

    List<ParishStaffAssignment> findByPersonIdOrderByResponsibilityAsc(Long personId);

    boolean existsByPersonIdAndActiveTrue(Long personId);

    List<ParishStaffAssignment> findByPersonIdAndActiveTrue(Long personId);

    boolean existsByPersonIdAndResponsibilityAndActiveTrue(Long personId, ParishResponsibilityType responsibility);

    /**
     * Leitura crua de todos os ParishStaffAssignment ativos de uma responsibility, sem o filtro
     * defensivo de {@code Person.active=true} usado em {@link #findActiveMembersByResponsibility}.
     * Existe especificamente para deteccao de integridade (ex.: mais de um PASTOR ativo): se a
     * verificacao dependesse apenas da projecao publica, um registro corrompido cuja Person tambem
     * estivesse inativa passaria despercebido (o filtro simplesmente o esconderia). {@code JOIN FETCH}
     * evita N+1 ao acessar {@code person} de cada resultado. Deliberadamente sem lock proprio: usada
     * dentro do bloco critico ja serializado pelo mutex {@code ParishProfile(id=1)} (ver
     * ParishStaffAssignmentServiceImpl) ou apenas para leitura diagnostica.
     */
    @Query("""
            SELECT a FROM ParishStaffAssignment a
            JOIN FETCH a.person
            WHERE a.responsibility = :responsibility
              AND a.active = TRUE
            """)
    List<ParishStaffAssignment> findByResponsibilityAndActiveTrue(
            @Param("responsibility") ParishResponsibilityType responsibility
    );

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
