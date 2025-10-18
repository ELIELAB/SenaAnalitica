package com.example.SenaAnalitica.service;



import com.example.SenaAnalitica.model.MegasenaResultado;
import com.example.SenaAnalitica.repository.MegasenaResultadoRepository;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SincronizacaoService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoService.class);

    private final RestTemplate restTemplate;
    private final MegasenaResultadoRepository repository;

    @Value("${megasena.api.url:https://loteriascaixa-api.herokuapp.com/api/megasena/}")
    private String apiUrl;

    public SincronizacaoService(RestTemplate restTemplate, MegasenaResultadoRepository repository) {
        this.restTemplate = restTemplate;
        this.repository = repository;
    }

    // DTO para mapear os campos da API
    @Data 
    private static class MegaSenaAPIDTO {
        private Integer concurso;
        private String data; // data_sorteio (precisa de parse no mapeamento)
        private Boolean acumulou; 
        private String cidade; 
        private String local; 
        private Double valorAcumulado; 
        private List<String> dezenas; 
    }

    public int verificarEAtualizar() {
        log.info("Iniciando a verificação e atualização da Mega-Sena...");
        int novosConcursos = 0;

        try {
            // 1. Encontrar o último concurso salvo no seu banco de dados
            Integer ultimoConcursoSalvo = repository.findTopByOrderByConcursoDesc()
                    .map(MegasenaResultado::getConcurso)
                    .orElse(0); 

            log.info("Último concurso salvo no banco: {}. Buscando novos a partir deste.", ultimoConcursoSalvo);

            // 2. Consumir a API
            List<MegaSenaAPIDTO> resultadosApi = restTemplate.exchange(
                    apiUrl,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<MegaSenaAPIDTO>>() {}
            ).getBody();

            if (resultadosApi == null || resultadosApi.isEmpty()) {
                log.warn("API retornou uma lista vazia ou nula.");
                return 0;
            }
            
            // 3. Filtrar e Mapear apenas os novos concursos
            List<MegasenaResultado> novosResultados = resultadosApi.stream()
                .filter(dto -> dto.getConcurso() != null && dto.getConcurso() > ultimoConcursoSalvo)
                .sorted(Comparator.comparing(MegaSenaAPIDTO::getConcurso))
                .map(dto -> {
                    // Mapeamento de DTO da API para Entidade do Banco de Dados
                    MegasenaResultado resultado = new MegasenaResultado();
                    resultado.setConcurso(dto.getConcurso());
                    resultado.setAcumulou(dto.getAcumulou());
                    resultado.setCidadeSorteio(dto.getCidade()); 
                    resultado.setDataSorteio(dto.getData()); // Usa o setter adaptado na Entidade
                    resultado.setLocalSorteio(dto.getLocal()); 
                    resultado.setValorAcumulado(dto.getValorAcumulado());
                    resultado.setDezenasSorteadas(dto.getDezenas() != null ? String.join(",", dto.getDezenas()) : null);
                    return resultado;
                })
                .collect(Collectors.toList());

            // 4. Salvar todos os novos resultados de uma vez
            if (!novosResultados.isEmpty()) {
                repository.saveAll(novosResultados);
                novosConcursos = novosResultados.size();
                log.info("Sincronização concluída. {} novos concursos foram adicionados.", novosConcursos);
            } else {
                log.info("Sincronização concluída. Nenhum novo concurso encontrado (Base atualizada).");
            }

        } catch (Exception e) {
            log.error("Erro ao sincronizar a API.", e);
        }

        return novosConcursos;
    }
}