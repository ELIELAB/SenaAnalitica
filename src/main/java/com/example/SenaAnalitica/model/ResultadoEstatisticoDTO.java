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
    
    // C.1 Frequência de Trincas 
    private Map<String, Long> frequenciaTrincas; 
    
    // C.2 Frequência de Quadras 
    private Map<String, Long> frequenciaQuadras; 

    // Média real da soma de todas as 6 dezenas
    private Double somaMediaSorteio; 

    // Desvio Padrão da soma de todas as 6 dezenas
    private Double desvioPadraoSoma;

    // (3): Média de números por quadrante 
    private Map<String, Double> mediaDistribuicaoSetores;
    
    // (4.1): Frequência de cada dígito final (0 a 9)
    private Map<String, Long> frequenciaDigitoFinal; 
    
    // (4.2): Frequência de cada dezena inicial (0 a 5)
    private Map<String, Long> frequenciaDigitoInicial; 

    // (5): Ciclo Médio de Recorrência (CMR) por dezena
    private Map<String, Double> cicloMedioRecorrencia; // Key ex: "43", Value ex: 20.4
}