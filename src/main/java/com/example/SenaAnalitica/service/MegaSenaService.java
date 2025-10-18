package com.example.SenaAnalitica.service;


import com.example.SenaAnalitica.model.MegasenaResultado;
import com.example.SenaAnalitica.model.ResultadoEstatisticoDTO;
import com.example.SenaAnalitica.repository.MegasenaResultadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger; // << NOVO IMPORT
import org.slf4j.LoggerFactory; // << NOVO IMPORT

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MegaSenaService {

    private static final Logger log = LoggerFactory.getLogger(MegaSenaService.class); // << INICIALIZAÇÃO DO LOGGER

    @Autowired
    private MegasenaResultadoRepository resultadoRepository;

    /**
     * Calcula as principais estatísticas (frequência, atraso e pares) do histórico salvo.
     */
    @Transactional(readOnly = true)
    public ResultadoEstatisticoDTO calcularEstatisticas() {
        
        // Esta linha aciona a leitura de todos os dados do banco
        List<MegasenaResultado> historico = resultadoRepository.findAll();
        
        if (historico.isEmpty()) {
            return ResultadoEstatisticoDTO.builder()
                    .totalConcursosAnalisados(0)
                    .frequenciaNumeros(Map.of())
                    .atrasoNumeros(Map.of())
                    .frequenciaPares(Map.of())
                    .mediaDezenas(30.5)
                    .build();
        }

        // Ordena para garantir que pegamos o último concurso pelo número
        historico.sort(Comparator.comparing(MegasenaResultado::getConcurso).reversed());
        MegasenaResultado ultimoConcurso = historico.get(0);
        
        // Converte a String de data para LocalDate para o DTO
        LocalDate ultimoSorteioDate = ultimoConcurso.getDataSorteioAsDate().orElse(null);
        
        // >>> DEBUG: ADICIONADO LOG PARA INSPECIONAR AS DEZENAS LIDOS DO BANCO <<<
        log.info("DEBUG: Dezenas salvas para o concurso {}: [{}], lista gerada: {}", 
                 ultimoConcurso.getConcurso(), 
                 ultimoConcurso.getDezenasSorteadas(), 
                 ultimoConcurso.getDezenasAsList());
        
        // 1. Contagem de Frequência (A.1)
        Map<String, Long> frequencia = historico.stream()
                .flatMap(c -> c.getDezenasAsList().stream())
                .collect(Collectors.groupingBy(
                        dezena -> dezena, 
                        Collectors.counting()
                ));

        // 2. Análise de Atraso (A.2)
        Map<String, Integer> atraso = calcularAtraso(historico, ultimoConcurso.getConcurso());
        
        // 3. Análise de Pares (A.3)
        Map<String, Long> frequenciaPares = calcularFrequenciaPares(historico);

        // 4. Monta e Retorna o DTO
        return ResultadoEstatisticoDTO.builder()
                .totalConcursosAnalisados(historico.size())
                .ultimoSorteio(ultimoSorteioDate) 
                .frequenciaNumeros(frequencia)
                .atrasoNumeros(atraso)
                .frequenciaPares(frequenciaPares) 
                .mediaDezenas(30.5) // Média Aritmética Teórica (constante)
                .build();
    }
    
    // --- Lógica Auxiliar de Cálculo de Atraso (A.2) ---

    private Map<String, Integer> calcularAtraso(List<MegasenaResultado> historico, Integer ultimoNumConcurso) {
        Map<String, Integer> atraso = new HashMap<>();
        
        IntStream.rangeClosed(1, 60)
            .forEach(i -> {
                String dezena = String.format("%02d", i);
                
                Optional<MegasenaResultado> ultimoSorteioDezena = historico.stream()
                        .filter(c -> c.getDezenasAsList().contains(dezena))
                        .max(Comparator.comparing(MegasenaResultado::getConcurso)); 
                
                int concursosDeAtraso = 0;
                if (ultimoSorteioDezena.isPresent()) {
                    concursosDeAtraso = ultimoNumConcurso - ultimoSorteioDezena.get().getConcurso();
                } else {
                    // Nunca foi sorteada (situação rara, mas possível)
                    concursosDeAtraso = ultimoNumConcurso; 
                }
                atraso.put(dezena, concursosDeAtraso);
            });
            
        return atraso;
    }

    // --- Lógica Auxiliar de Cálculo de Frequência de Pares (A.3) ---

    private Map<String, Long> calcularFrequenciaPares(List<MegasenaResultado> historico) {
        Map<String, Long> frequenciaPares = new HashMap<>();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());

            // Gera todos os pares (combinação de 2)
            for (int i = 0; i < dezenas.size(); i++) {
                for (int j = i + 1; j < dezenas.size(); j++) {
                    String par = String.format("%02d-%02d", dezenas.get(i), dezenas.get(j));
                    frequenciaPares.merge(par, 1L, Long::sum);
                }
            }
        }
        return frequenciaPares;
    }
}