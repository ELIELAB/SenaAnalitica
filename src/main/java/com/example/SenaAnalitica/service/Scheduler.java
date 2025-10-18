package com.example.SenaAnalitica.service;



import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);
    private final SincronizacaoService sincronizacaoService;

    public Scheduler(SincronizacaoService sincronizacaoService) {
        this.sincronizacaoService = sincronizacaoService;
    }

    // Agendamento: Executa a cada 6 horas (21600000 ms).
    @Scheduled(fixedRate = 21600000) 
    public void verificarNovosResultados() {
        log.info("Iniciando a tarefa agendada de sincronização de resultados.");
        sincronizacaoService.verificarEAtualizar();
    }
}