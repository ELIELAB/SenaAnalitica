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
import java.util.LinkedHashMap;
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
     * Calcula as principais estatísticas (frequência, atraso, pares, trincas, quadras e EQUILÍBRIO DE SOMA) do histórico salvo.
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
                    .somaMediaSorteio(183.0) 
                    .desvioPadraoSoma(0.0)
                    .mediaDistribuicaoSetores(Map.of())
                    .frequenciaDigitoFinal(Map.of())
                    .frequenciaDigitoInicial(Map.of())
                    .build();
        }

        historico.sort(Comparator.comparing(MegasenaResultado::getConcurso).reversed());
        MegasenaResultado ultimoConcurso = historico.get(0);

        LocalDate ultimoSorteioDate = ultimoConcurso.getDataSorteioAsDate().orElse(null);

        log.info("DEBUG: Dezenas salvas para o concurso {}: [{}], lista gerada: {}", 
                 ultimoConcurso.getConcurso(), 
                 ultimoConcurso.getDezenasSorteadas(), 
                 ultimoConcurso.getDezenasAsList());

        // --- 1. CÁLCULO DAS SOMAS E DP ---
        List<Double> somasPorSorteio = historico.stream()
                .map(c -> c.getDezenasAsList().stream()
                        .mapToInt(s -> {
                            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
                        })
                        .sum())
                .map(Integer::doubleValue)
                .collect(Collectors.toList());

        double somaTotal = somasPorSorteio.stream().mapToDouble(Double::doubleValue).sum();
        int totalSorteios = somasPorSorteio.size();

        // Média da Soma
        Double somaMedia = totalSorteios > 0 ? somaTotal / totalSorteios : 183.0;

        // Desvio Padrão da Soma
        Double desvioPadrao = calcularDesvioPadraoSoma(somasPorSorteio, somaMedia);

        // --- 2. CÁLCULO DAS ESTATÍSTICAS DE FREQUÊNCIA ---

        // Frequência Simples
        Map<String, Long> frequencia = historico.stream()
                .flatMap(c -> c.getDezenasAsList().stream())
                .collect(Collectors.groupingBy(
                        dezena -> dezena, 
                        Collectors.counting()
                ));

        // Atraso
        Map<String, Integer> atraso = calcularAtraso(historico, ultimoConcurso.getConcurso());

        // Pares
        Map<String, Long> frequenciaPares = calcularFrequenciaPares(historico);

        // Trincas e Quadras
        Map<String, Long> rawFrequenciaTrincas = calcularFrequenciaTrincas(historico);
        Map<String, Long> topTrincas = filtrarTopN(rawFrequenciaTrincas, 100);

        Map<String, Long> rawFrequenciaQuadras = calcularFrequenciaQuadras(historico);
        Map<String, Long> topQuadras = filtrarTopN(rawFrequenciaQuadras, 100);

        // --- 3. CÁLCULO DA DISTRIBUIÇÃO DE SETORES ---
        Map<String, Double> mediaDistribuicaoSetores = calcularMediaDistribuicaoSetores(historico);
        
        // --- 4. CÁLCULO DE FIM E INÍCIO (NOVO) ---
        Map<String, Long> frequenciaDigitoFinal = calcularFrequenciaDigitoFinal(historico);
        Map<String, Long> frequenciaDigitoInicial = calcularFrequenciaDigitoInicial(historico);

        // --- 5. MONTA E RETORNA O DTO ---
        return ResultadoEstatisticoDTO.builder()
                .totalConcursosAnalisados(historico.size())
                .ultimoSorteio(ultimoSorteioDate) 
                .frequenciaNumeros(frequencia)
                .atrasoNumeros(atraso)
                .frequenciaPares(frequenciaPares)
                .frequenciaTrincas(topTrincas) 
                .frequenciaQuadras(topQuadras) 
                .mediaDezenas(30.5) // Média Aritmética Teórica (constante)
                .somaMediaSorteio(somaMedia) 
                .desvioPadraoSoma(desvioPadrao) 
                .mediaDistribuicaoSetores(mediaDistribuicaoSetores)
                .frequenciaDigitoFinal(frequenciaDigitoFinal) 
                .frequenciaDigitoInicial(frequenciaDigitoInicial) 
                .build();
    }
    
    // --- MÉTODOS AUXILIARES PARA ANÁLISE DE FIM E INÍCIO (4) ---

    /**
     * Calcula a frequência de cada dígito final (0 a 9) em todo o histórico.
     */
    private Map<String, Long> calcularFrequenciaDigitoFinal(List<MegasenaResultado> historico) {
        
        Map<String, Long> frequencia = historico.stream()
                .flatMap(c -> c.getDezenasAsList().stream())
                .map(dezena -> dezena.substring(dezena.length() - 1))
                .collect(Collectors.groupingBy(
                        digito -> digito, 
                        Collectors.counting()
                ));
        
        IntStream.rangeClosed(0, 9).forEach(i -> {
            String digito = String.valueOf(i);
            frequencia.putIfAbsent(digito, 0L);
        });

        return frequencia.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (e1, e2) -> e1, 
                        LinkedHashMap::new
                ));
    }

    /**
     * Calcula a frequência de cada dezena inicial (dezenas 0-5) em todo o histórico.
     */
    private Map<String, Long> calcularFrequenciaDigitoInicial(List<MegasenaResultado> historico) {
        
        Map<String, Long> frequencia = historico.stream()
                .flatMap(c -> c.getDezenasAsList().stream())
                .map(dezena -> {
                    try {
                        int num = Integer.parseInt(dezena.trim());
                        // 01-09 -> "0", 10-19 -> "1", ..., 50-59 -> "5"
                        if (num == 60) return "5"; 
                        return String.valueOf(num / 10);
                    } catch (NumberFormatException e) {
                        return null; 
                    }
                })
                .filter(prefixo -> prefixo != null && prefixo.matches("[0-5]")) 
                .collect(Collectors.groupingBy(
                        prefixo -> prefixo, 
                        Collectors.counting()
                ));
        
        IntStream.rangeClosed(0, 5).forEach(i -> {
            String prefixo = String.valueOf(i);
            frequencia.putIfAbsent(prefixo, 0L);
        });
        
        return frequencia.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (e1, e2) -> e1, 
                        LinkedHashMap::new
                ));
    }
    
    // --- MÉTODOS AUXILIARES PARA ANÁLISE DE QUADRANTES/SETORES (3) ---

    /**
     * Calcula a média de números sorteados em cada quadrante (01-15, 16-30, 31-45, 46-60).
     */
    private Map<String, Double> calcularMediaDistribuicaoSetores(List<MegasenaResultado> historico) {

        Map<String, Integer> limitesQuadrantes = new LinkedHashMap<>();
        limitesQuadrantes.put("1-15", 15);
        limitesQuadrantes.put("16-30", 30);
        limitesQuadrantes.put("31-45", 45);
        limitesQuadrantes.put("46-60", 60);

        Map<String, Long> contagemTotalPorSetor = new HashMap<>();
        limitesQuadrantes.keySet().forEach(key -> contagemTotalPorSetor.put(key, 0L));

        int totalConcursos = historico.size();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(s -> {
                        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
                    })
                    .filter(d -> d >= 1 && d <= 60)
                    .collect(Collectors.toList());

            for (Integer dezena : dezenas) {
                
                String setor = null;

                for (Map.Entry<String, Integer> entry : limitesQuadrantes.entrySet()) {
                    int limiteInferior = entry.getValue() - 14; 
                    int limiteSuperior = entry.getValue();

                    if (dezena >= limiteInferior && dezena <= limiteSuperior) {
                        setor = entry.getKey();
                        break;
                    }
                }

                if (setor != null) {
                    contagemTotalPorSetor.merge(setor, 1L, Long::sum);
                }
            }
        }

        Map<String, Double> mediaDistribuicao = new LinkedHashMap<>();
        if (totalConcursos > 0) {
            contagemTotalPorSetor.forEach((setor, totalOcorrencias) -> {
                double media = (double) totalOcorrencias / totalConcursos;
                mediaDistribuicao.put(setor, Math.round(media * 100.0) / 100.0);
            });
        }

        return mediaDistribuicao;
    }

    // --- MÉTODOS AUXILIARES DE LÓGICA EXISTENTE ---
    
    /**
     * Calcula o Desvio Padrão de uma lista de somas.
     */
    private Double calcularDesvioPadraoSoma(List<Double> somas, Double media) {
        if (somas == null || somas.isEmpty() || media == null) {
            return 0.0;
        }

        double somaDiferencasQuadradas = somas.stream()
                .mapToDouble(soma -> Math.pow(soma - media, 2))
                .sum();

        return Math.sqrt(somaDiferencasQuadradas / somas.size());
    }

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

    private Map<String, Long> calcularFrequenciaPares(List<MegasenaResultado> historico) {
        Map<String, Long> frequenciaPares = new HashMap<>();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());

            for (int i = 0; i < dezenas.size(); i++) {
                for (int j = i + 1; j < dezenas.size(); j++) {
                    String par = formatarDezena(dezenas.get(i)) + "-" + formatarDezena(dezenas.get(j));
                    frequenciaPares.merge(par, 1L, Long::sum);
                }
            }
        }
        return frequenciaPares;
    }

    private Map<String, Long> calcularFrequenciaTrincas(List<MegasenaResultado> historico) {
        Map<String, Long> frequenciaTrincas = new HashMap<>();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());

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

    private Map<String, Long> calcularFrequenciaQuadras(List<MegasenaResultado> historico) {
        Map<String, Long> frequenciaQuadras = new HashMap<>();

        for (MegasenaResultado resultado : historico) {
            List<Integer> dezenas = resultado.getDezenasAsList().stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());

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

    private Map<String, Long> filtrarTopN(Map<String, Long> map, int limit) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, 
                        LinkedHashMap::new
                ));
    }

    private String formatarDezena(Integer dezena) {
        return String.format("%02d", dezena);
    }
}