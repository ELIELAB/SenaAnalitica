package com.example.SenaAnalitica.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class ResultadoEstatisticoDTO {

    private Integer totalConcursosAnalisados;
    private LocalDate ultimoSorteio;

    // A.1 Frequência de cada dezena (Ex: "05": 300)
    private Map<String, Long> frequenciaNumeros;
    
    // A.2 Atraso de cada dezena (Ex: "05": 5)
    private Map<String, Integer> atrasoNumeros;
    
    // A.3 Frequência de pares (Ex: "05-10": 15)
    private Map<String, Long> frequenciaPares;
    
    // B. Média Aritmética Teórica
    private Double mediaDezenas; 
    
    // C.1 Frequência de Trincas (NOVO)
    private Map<String, Long> frequenciaTrincas; // Key ex: "05-10-15"
    
    // C.2 Frequência de Quadras (NOVO)
    private Map<String, Long> frequenciaQuadras; // Key ex: "05-10-15-20"

    // NOVO: Média real da soma de todas as 6 dezenas
    private Double somaMediaSorteio; 

    // NOVO: Desvio Padrão da soma de todas as 6 dezenas
    private Double desvioPadraoSoma;

    // NOVO (3): Média de números por quadrante (01-15, 16-30, etc.)
    private Map<String, Double> mediaDistribuicaoSetores;
}
