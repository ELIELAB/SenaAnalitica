package com.example.SenaAnalitica.service;

import com.example.SenaAnalitica.model.MegasenaResultado;
import com.example.SenaAnalitica.model.ResultadoEstatisticoDTO;
import com.example.SenaAnalitica.repository.MegasenaResultadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap; // Adicionado para manter a ordem do sort
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MegaSenaService {

    private static final Logger log = LoggerFactory.getLogger(MegaSenaService.class);

    @Autowired
    private MegasenaResultadoRepository resultadoRepository;

    /**
     * Calcula as principais estatísticas (frequência, atraso, pares, trincas e quadras) do histórico salvo.
     */
    @Transactional(readOnly = true)
    public ResultadoEstatisticoDTO calcularEstatisticas() {
        
        List<MegasenaResultado> historico = resultadoRepository.findAll();
        
        if (historico.isEmpty()) {
            return ResultadoEstatisticoDTO.builder()
                    .totalConcursosAnalisados(0)
                    .frequenciaNumeros(Map.of())
                    .atrasoNumeros(Map.of())
                    .frequenciaPares(Map.of())
                    .frequenciaTrincas(Map.of())
                    .frequenciaQuadras(Map.of())
                    .mediaDezenas(30.5)
                    .build();
        }

        historico.sort(Comparator.comparing(MegasenaResultado::getConcurso).reversed());
        MegasenaResultado ultimoConcurso = historico.get(0);
        
        LocalDate ultimoSorteioDate = ultimoConcurso.getDataSorteioAsDate().orElse(null);
        
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
        
        // 4. Análise de Trincas (NOVO)
        Map<String, Long> rawFrequenciaTrincas = calcularFrequenciaTrincas(historico);
        Map<String, Long> topTrincas = filtrarTopN(rawFrequenciaTrincas, 100);
        
        // 5. Análise de Quadras (NOVO)
        Map<String, Long> rawFrequenciaQuadras = calcularFrequenciaQuadras(historico);
        Map<String, Long> topQuadras = filtrarTopN(rawFrequenciaQuadras, 100);

        // 6. Monta e Retorna o DTO
        return ResultadoEstatisticoDTO.builder()
                .totalConcursosAnalisados(historico.size())
                .ultimoSorteio(ultimoSorteioDate) 
                .frequenciaNumeros(frequencia)
                .atrasoNumeros(atraso)
                .frequenciaPares(frequenciaPares)
                .frequenciaTrincas(topTrincas) // Agora filtrado
                .frequenciaQuadras(topQuadras) // Agora filtrado
                .mediaDezenas(30.5) 
                .build();
    }
    
    /**
     * Filtra o mapa para retornar apenas os top N elementos, ordenados por frequência (valor) decrescente.
     * @param map Mapa completo de frequência.
     * @param limit O número máximo de elementos a retornar.
     * @return Um novo mapa LinkedHashMap com os resultados ordenados e limitados.
     */
    private Map<String, Long> filtrarTopN(Map<String, Long> map, int limit) {
        return map.entrySet().stream()
                // 1. Ordena pela frequência (valor) decrescente
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                // 2. Limita aos N primeiros
                .limit(limit)
                // 3. Coleta para um LinkedHashMap para manter a ordem
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, // Função de merge, não deve ser chamada aqui
                        LinkedHashMap::new
                ));
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
                    String par = formatarDezena(dezenas.get(i)) + "-" + formatarDezena(dezenas.get(j));
                    frequenciaPares.merge(par, 1L, Long::sum);
                }
            }
        }
        return frequenciaPares;
    }

    // --- Lógica Auxiliar de Cálculo de Frequência de Trincas (Novo) ---

    private Map<String, Long> calcularFrequenciaTrincas(List<MegasenaResultado> historico) {
        Map<String, Long> frequenciaTrincas = new HashMap<>();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());

            // Combinação de 3: C(6, 3) = 20
            for (int i = 0; i < 4; i++) { 
                for (int j = i + 1; j < 5; j++) {
                    for (int k = j + 1; k < 6; k++) {
                        String trinca = formatarDezena(dezenas.get(i)) + "-" + 
                                        formatarDezena(dezenas.get(j)) + "-" + 
                                        formatarDezena(dezenas.get(k));
                        frequenciaTrincas.merge(trinca, 1L, Long::sum);
                    }
                }
            }
        }
        return frequenciaTrincas;
    }
    
    // --- Lógica Auxiliar de Cálculo de Frequência de Quadras (Novo) ---

    private Map<String, Long> calcularFrequenciaQuadras(List<MegasenaResultado> historico) {
        Map<String, Long> frequenciaQuadras = new HashMap<>();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());

            // Combinação de 4: C(6, 4) = 15
            for (int i = 0; i < 3; i++) {
                for (int j = i + 1; j < 4; j++) {
                    for (int k = j + 1; k < 5; k++) {
                        for (int l = k + 1; l < 6; l++) {
                            String quadra = formatarDezena(dezenas.get(i)) + "-" + 
                                            formatarDezena(dezenas.get(j)) + "-" + 
                                            formatarDezena(dezenas.get(k)) + "-" +
                                            formatarDezena(dezenas.get(l));
                            frequenciaQuadras.merge(quadra, 1L, Long::sum);
                        }
                    }
                }
            }
        }
        return frequenciaQuadras;
    }
    
    /**
     * Formata um número inteiro em uma string de dois dígitos (ex: 5 -> "05").
     */
    private String formatarDezena(Integer dezena) {
        return String.format("%02d", dezena);
    }
}
