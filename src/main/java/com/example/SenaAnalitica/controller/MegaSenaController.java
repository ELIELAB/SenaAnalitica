package com.example.SenaAnalitica.controller;

import com.example.SenaAnalitica.model.ResultadoEstatisticoDTO;
import com.example.SenaAnalitica.service.MegaSenaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/megasena")
// Permite que o frontend em outra porta (como 8080 ou um arquivo local) acesse a API
@CrossOrigin(origins = "*", maxAge = 3600)
public class MegaSenaController {

    @Autowired
    private MegaSenaService megaSenaService;

    @GetMapping("/estatisticas")
    public ResponseEntity<ResultadoEstatisticoDTO> getEstatisticas() {
        
        // Chama o novo serviço de cálculo
        ResultadoEstatisticoDTO estatisticas = megaSenaService.calcularEstatisticas();
        
        // Retorna o objeto DTO com status HTTP 200 OK
        return ResponseEntity.ok(estatisticas);
    }
}