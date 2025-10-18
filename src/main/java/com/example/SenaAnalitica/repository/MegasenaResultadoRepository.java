package com.example.SenaAnalitica.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.SenaAnalitica.model.MegasenaResultado;
import java.util.Optional;

@Repository
public interface MegasenaResultadoRepository extends JpaRepository<MegasenaResultado, Integer> {
    
    // Para otimização de busca
    Optional<MegasenaResultado> findTopByOrderByConcursoDesc();
}