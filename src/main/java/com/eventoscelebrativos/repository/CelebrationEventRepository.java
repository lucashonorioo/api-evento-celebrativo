package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EventoEscalaMinistrosProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface EventoCelebrativoRepository extends JpaRepository<CelebrationEvent, Long> {

    @Query(nativeQuery = true, value = """
        SELECT 
            e.nome_missa_ou_evento AS nomeEvento,
            e.data_evento AS dataEvento,
            e.hora_evento AS horaEvento,
            l.nome_da_igreja AS nomeIgreja,
            p.nome AS nomeMinistro
        FROM 
            tb_evento_celebrativo e
        INNER JOIN 
            tb_evento_local el ON e.id = el.evento_id
        INNER JOIN 
            tb_local l ON l.id = el.local_id
        INNER JOIN 
            tb_evento_pessoa ep ON e.id = ep.evento_id
        INNER JOIN 
            tb_pessoa p ON ep.pessoa_id = p.id
        WHERE 
            p.tipo = 'ministro_de_eucaristia'
            AND e.data_evento BETWEEN :dataInicial AND :dataFinal
        ORDER BY 
            e.nome_missa_ou_evento, e.data_evento
    """)
    Page<EventoEscalaMinistrosProjection> buscarEscalaMinistro(Pageable pageable, LocalDate dataInicial, LocalDate dataFinal);

}
