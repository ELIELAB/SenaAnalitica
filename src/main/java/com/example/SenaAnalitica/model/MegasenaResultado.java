package com.example.SenaAnalitica.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors; // Adicionado para coleta

@Entity 
@Table(name = "megasena_resultado")
@Data 
@NoArgsConstructor
@AllArgsConstructor
public class MegasenaResultado {

    @Id
    private Integer concurso; 
    
    @Column(name = "acumulou")
    private Boolean acumulou;

    @Column(name = "cidade_sorteio")
    private String cidadeSorteio; 
    
    @Column(name = "data_sorteio")
    private String dataSorteio; 
    
    @Column(name = "local_sorteio")
    private String localSorteio; 

    @Column(name = "valor_acumulado")
    private Double valorAcumulado; 

    @Column(name = "dezenas_sorteadas")
    private String dezenasSorteadas; 
    
    // --- Métodos Auxiliares ---
    
    private static final DateTimeFormatter API_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Método CORRIGIDO para ler a String do DB e transformar em List
    public List<String> getDezenasAsList() {
        if (this.dezenasSorteadas == null || this.dezenasSorteadas.isEmpty()) {
            return List.of();
        }
        
        // CORREÇÃO: Usamos o stream para:
        // 1. Dividir pelo separador de vírgula (",")
        // 2. Mapear cada elemento, removendo espaços em branco (trim())
        // 3. Filtrar para garantir que não estamos processando strings vazias
        return Arrays.stream(this.dezenasSorteadas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // Método auxiliar para o Service de Análise (retorna a data como LocalDate para cálculos)
    public Optional<LocalDate> getDataSorteioAsDate() {
        if (this.dataSorteio == null) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(this.dataSorteio.trim(), API_FORMATTER));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}