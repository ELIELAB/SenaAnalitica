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
import java.util.ArrayList;
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

        // Ordena o histórico pelo número do concurso de forma crescente (do mais antigo para o mais novo)
        List<MegasenaResultado> historico = resultadoRepository.findAll();
        historico.sort(Comparator.comparing(MegasenaResultado::getConcurso));
        
        if (historico.isEmpty()) {
            log.warn("Nenhum registro de resultado encontrado no banco de dados. Retornando DTO vazio.");
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
                    .cicloMedioRecorrencia(Map.of())
                    .build();
        }

        // Pega o último elemento (maior concurso) da lista ordenada para cálculos de atraso
        MegasenaResultado ultimoConcurso = historico.get(historico.size() - 1);
        log.info("Iniciando cálculo estatístico. Total de concursos: {}. Último concurso: {}", historico.size(), ultimoConcurso.getConcurso());

        LocalDate ultimoSorteioDate = ultimoConcurso.getDataSorteioAsDate().orElse(null);

        // --- 1. CÁLCULO DAS SOMAS E DP ---
        List<Double> somasPorSorteio = historico.stream()
                .map(c -> c.getDezenasAsList().stream()
                        .mapToInt(s -> {
                            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) {
                                log.error("Erro de formatação de número em concurso {}: {}", c.getConcurso(), s);
                                return 0; 
                            }
                        })
                        .sum())
                .map(Integer::doubleValue)
                .collect(Collectors.toList());

        double somaTotal = somasPorSorteio.stream().mapToDouble(Double::doubleValue).sum();
        int totalSorteios = somasPorSorteio.size();

        Double somaMedia = totalSorteios > 0 ? somaTotal / totalSorteios : 183.0;
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
        Map<String, Long> frequenciaTrincas = calcularFrequenciaTrincas(historico);
        Map<String, Long> frequenciaQuadras = calcularFrequenciaQuadras(historico);

        // --- 3. CÁLCULO DA DISTRIBUIÇÃO DE SETORES ---
        Map<String, Double> mediaDistribuicaoSetores = calcularMediaDistribuicaoSetores(historico);
        
        // --- 4. CÁLCULO DE FIM E INÍCIO ---
        Map<String, Long> frequenciaDigitoFinal = calcularFrequenciaDigitoFinal(historico);
        Map<String, Long> frequenciaDigitoInicial = calcularFrequenciaDigitoInicial(historico);
        
        // --- 5. CICLO MÉDIO DE RECORRÊNCIA ---
        Map<String, Double> cicloMedioRecorrencia = calcularCicloMedioRecorrencia(historico);


        // --- 6. MONTA E RETORNA O DTO ---
        log.info("Cálculos concluídos. Retornando DTO estatístico.");
        return ResultadoEstatisticoDTO.builder()
                .totalConcursosAnalisados(historico.size())
                .ultimoSorteio(ultimoSorteioDate) 
                .frequenciaNumeros(frequencia)
                .atrasoNumeros(atraso)
                .frequenciaPares(frequenciaPares)
                .frequenciaTrincas(frequenciaTrincas) 
                .frequenciaQuadras(frequenciaQuadras) 
                .mediaDezenas(30.5) 
                .somaMediaSorteio(somaMedia) 
                .desvioPadraoSoma(desvioPadrao) 
                .mediaDistribuicaoSetores(mediaDistribuicaoSetores)
                .frequenciaDigitoFinal(frequenciaDigitoFinal) 
                .frequenciaDigitoInicial(frequenciaDigitoInicial) 
                .cicloMedioRecorrencia(cicloMedioRecorrencia) 
                .build();
    }
    
    // --- MÉTODOS AUXILIARES PARA CICLO MÉDIO DE RECORRÊNCIA (5) ---

    private Map<String, Double> calcularCicloMedioRecorrencia(List<MegasenaResultado> historico) {
        
        Map<String, List<Integer>> aparicoesPorDezena = new HashMap<>();
        IntStream.rangeClosed(1, 60).forEach(i -> {
            aparicoesPorDezena.put(formatarDezena(i), new ArrayList<>());
        });

        for (MegasenaResultado resultado : historico) {
            for (String dezena : resultado.getDezenasAsList()) {
                aparicoesPorDezena.getOrDefault(dezena, new ArrayList<>()).add(resultado.getConcurso());
            }
        }
        
        Map<String, Double> cmrMap = new LinkedHashMap<>();

        for (Map.Entry<String, List<Integer>> entry : aparicoesPorDezena.entrySet()) {
            String dezena = entry.getKey();
            List<Integer> concursos = entry.getValue();
            
            if (concursos.isEmpty()) {
                log.warn("Dezena {} nunca foi sorteada em {} concursos!", dezena, historico.size());
            }

            if (concursos.size() < 2) {
                double cmr = (double) historico.size();
                cmrMap.put(dezena, Math.round(cmr * 100.0) / 100.0);
                continue;
            }
            
            List<Integer> diferencas = new ArrayList<>();
            for (int i = 1; i < concursos.size(); i++) {
                int diferenca = concursos.get(i) - concursos.get(i - 1);
                diferencas.add(diferenca);
            }
            
            double somaDiferencas = diferencas.stream().mapToInt(Integer::intValue).sum();
            double cmr = somaDiferencas / diferencas.size();
            
            cmrMap.put(dezena, Math.round(cmr * 100.0) / 100.0);
        }
        
        return cmrMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (e1, e2) -> e1, 
                        LinkedHashMap::new
                ));
    }
    
    // --- MÉTODOS AUXILIARES PARA ANÁLISE DE FIM E INÍCIO (4) ---

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

    private Map<String, Long> calcularFrequenciaDigitoInicial(List<MegasenaResultado> historico) {
        
        Map<String, Long> frequencia = historico.stream()
                .flatMap(c -> c.getDezenasAsList().stream())
                .map(dezena -> {
                    try {
                        int num = Integer.parseInt(dezena.trim());
                        if (num == 60) return "5"; 
                        return String.valueOf(num / 10);
                    } catch (NumberFormatException e) {
                        log.error("Erro de formatação de dígito inicial.");
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
        
        if (totalConcursos == 0) {
            log.warn("Cálculo de distribuição de setores não pôde ser realizado: 0 concursos.");
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
            log.warn("Cálculo de Desvio Padrão não pôde ser realizado: dados nulos/vazios.");
            return 0.0;
        }
        // ... (resto da lógica)
        double somaDiferencasQuadradas = somas.stream()
                .mapToDouble(soma -> Math.pow(soma - media, 2))
                .sum();

        return Math.sqrt(somaDiferencasQuadradas / somas.size());
    }

    private Map<String, Integer> calcularAtraso(List<MegasenaResultado> historico, Integer ultimoNumConcurso) {
        Map<String, Integer> atraso = new HashMap<>();

        IntStream.rangeClosed(1, 60)
            .forEach(i -> {
                String dezena = formatarDezena(i);

                Optional<MegasenaResultado> ultimoSorteioDezena = historico.stream()
                        .filter(c -> c.getDezenasAsList().contains(dezena))
                        .max(Comparator.comparing(MegasenaResultado::getConcurso)); 

                int concursosDeAtraso = 0;
                if (ultimoSorteioDezena.isPresent()) {
                    concursosDeAtraso = ultimoNumConcurso - ultimoSorteioDezena.get().getConcurso();
                } else {
                    log.debug("Dezena {} nunca foi sorteada. Atraso total: {}", dezena, ultimoNumConcurso);
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

    private String formatarDezena(Integer dezena) {
        return String.format("%02d", dezena);
    }
}