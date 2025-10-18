const API_URL = 'http://localhost:8080/api/megasena/estatisticas'; 

// Função auxiliar para formatar números (ex: 1 -> "01")
function formatDezena(num) {
    return String(num).padStart(2, '0');
}

// Helper para obter o setor (quadrante)
function getSetor(dezena) {
    const num = parseInt(dezena);
    if (num <= 15) return "1-15";
    if (num <= 30) return "16-30";
    if (num <= 45) return "31-45";
    return "46-60";
}

// Helper para obter o dígito inicial
function getDigitoInicial(dezena) {
     const num = parseInt(dezena);
     if (num === 60) return "5";
     return String(Math.floor(num / 10));
}

// Função principal para buscar e renderizar os dados
async function carregarEstatisticas() {
    try {
        const response = await fetch(API_URL);
        if (!response.ok) {
            throw new Error(`Erro HTTP! Status: ${response.status}. Verifique se a API está rodando na porta 8080.`);
        }
        const data = await response.json();
        
        document.getElementById('info-geral').innerHTML = 
            `Último Sorteio Analisado: <span class="data-label">${data.ultimoSorteio}</span> | 
             Total de Concursos: <span class="data-label">${data.totalConcursosAnalisados}</span>`;

        // Renderizações
        renderResumoEstatistico(data);
        renderFrequenciaEAtrazo(data);
        renderParesETrincas(data);
        renderDigitosFimEInicio(data); 
        
        // Geração e renderização das 3 Apostas Inteligentes
        gerarApostaInteligente(data);

    } catch (error) {
        console.error("Erro ao buscar dados da API:", error);
        document.getElementById('info-geral').innerHTML = 
            `<span class="highlight-red">ERRO:</span> Não foi possível conectar à API. Detalhes: ${error.message}`;
        
        // Remove os spinners em caso de erro
        document.getElementById('aposta-critico').innerHTML = `<span class="highlight-red">ERRO</span>`;
        document.getElementById('aposta-quente').innerHTML = `<span class="highlight-red">ERRO</span>`;
        document.getElementById('aposta-mista').innerHTML = `<span class="highlight-red">ERRO</span>`;
        document.getElementById('estrategia-critico').innerHTML = `Não foi possível gerar a aposta.`;
        document.getElementById('estrategia-quente').innerHTML = `Não foi possível gerar a aposta.`;
        document.getElementById('estrategia-mista').innerHTML = `Não foi possível gerar a aposta.`;
    }
}


// --- 5. FUNÇÕES DE GERAÇÃO DAS 3 APOSTAS INTELIGENTES (HEURÍSTICA) ---

// Função principal que gera as 3 apostas
function gerarApostaInteligente(data) {
    
    const totalConcursos = data.totalConcursosAnalisados;
    const somaMedia = data.somaMediaSorteio;
    const somaDP = data.desvioPadraoSoma;
    const limiteInferior = Math.round(somaMedia - somaDP);
    const limiteSuperior = Math.round(somaMedia + somaDP);
    
    // 1. Prepara todos os números com suas métricas
    const numsData = Array.from({ length: 60 }, (_, i) => formatDezena(i + 1)).map(dezena => {
        const cmr = data.cicloMedioRecorrencia[dezena] || totalConcursos;
        const atraso = data.atrasoNumeros[dezena] || 0;
        const frequencia = data.frequenciaNumeros[dezena] || 0;
        const criticidade = atraso - cmr; 
        
        return { dezena, atraso, cmr, criticidade, frequencia };
    });

    // --- GERAÇÃO DAS 3 APOSTAS ---
    const apostaCritico = gerarApostaCritico(numsData, data);
    const apostaQuente = gerarApostaQuente(numsData, data);
    const apostaMista = gerarApostaMista(numsData, data);

    // --- RENDERIZAÇÃO FINAL ---
    renderAposta(apostaCritico, 'aposta-critico', 'estrategia-critico', limiteInferior, limiteSuperior, 'bg-danger');
    renderAposta(apostaQuente, 'aposta-quente', 'estrategia-quente', limiteInferior, limiteSuperior, 'bg-warning');
    renderAposta(apostaMista, 'aposta-mista', 'estrategia-mista', limiteInferior, limiteSuperior, 'bg-primary');
}

// --- LÓGICA GERAL (Função que respeita regras de equilíbrio E SEQUÊNCIA) ---
function criarAposta(numsPrioridade, pares, logBase, motivoFrio, motivoQuente, maxSetor = 2, maxInicial = 2) {
    let aposta = [];
    let log = [...logBase];
    const maxSetorFinal = maxSetor; 
    const maxInicialFinal = maxInicial; 
    
    // Helper para checar equilíbrio e **NOVO: ANTI-SEQUÊNCIA**
    function isEquilibrado(dezena, checkSequence = true) {
        const num = parseInt(dezena);
        const setor = getSetor(dezena);
        const inicial = getDigitoInicial(dezena);
        
        const countSetor = aposta.filter(d => getSetor(d) === setor).length;
        const countInicial = aposta.filter(d => getDigitoInicial(d) === inicial).length;
        
        // 1. Regra de Sequência: Verifica se o número é adjacente a um número já escolhido
        if (checkSequence) {
            const isSequencia = aposta.some(d => {
                const outro = parseInt(d);
                return outro === num + 1 || outro === num - 1;
            });
            if (isSequencia) {
                return false;
            }
        }

        // 2. Regra de Distribuição
        return countSetor < maxSetorFinal && countInicial < maxInicialFinal;
    }

    // 1. Adicionar números da Prioridade (CMR/Frequência)
    for (let i = 0; aposta.length < 4 && i < numsPrioridade.length; i++) {
        const num = numsPrioridade[i];
        if (!aposta.includes(num.dezena) && isEquilibrado(num.dezena)) {
            aposta.push(num.dezena);
            const motivo = i === 0 ? motivoFrio : motivoQuente;
            log.push(`- ${num.dezena}: ${motivo}`);
        }
    }
    
    // 2. Tentar incluir o Par mais forte
    for (const [parStr] of pares) {
        const [d1, d2] = parStr.split('-');
        
        // Tenta incluir o par completo no meio da aposta
        if (aposta.length < 5 && !aposta.includes(d1) && !aposta.includes(d2) && isEquilibrado(d1) && isEquilibrado(d2)) {
             aposta.push(d1, d2);
             log.push(`- ${d1} e ${d2}: PAR RECORRENTE (${parStr})`);
             break;
        } 
        // Tentar incluir o fechamento de par
        else if (aposta.length === 5) {
             // Passamos 'false' para checkSequence para permitir que o fechamento do par crie sequência, se for o caso
             if (aposta.includes(d1) && !aposta.includes(d2) && isEquilibrado(d2, false)) { 
                 aposta.push(d2);
                 log.push(`- ${d2}: FECHAMENTO DE PAR (${parStr})`);
                 break;
             } else if (aposta.includes(d2) && !aposta.includes(d1) && isEquilibrado(d1, false)) {
                 aposta.push(d1);
                 log.push(`- ${d1}: FECHAMENTO DE PAR (${parStr})`);
                 break;
             }
        }
        if (aposta.length === 6) break;
    }

    // 3. Preenchimento de Equilíbrio
    while (aposta.length < 6) {
        const disponiveis = numsPrioridade.filter(n => !aposta.includes(n.dezena));
        const friosRestantes = disponiveis.sort((a, b) => b.criticidade - a.criticidade);

        let numeroEscolhido = null;

        // Tenta achar o melhor número para o equilíbrio (que não cria sequência)
        for (const { dezena } of friosRestantes) {
            // A regra isEquilibrado já verifica a sequência
            if (isEquilibrado(dezena)) {
                numeroEscolhido = dezena;
                break; 
            }
        }
        
        // Se não encontrou o 'melhor' equilibrado (porque a sequência e distribuição bloquearam), 
        // relaxamos a regra do isSequencia APENAS se for o último slot.
        if (!numeroEscolhido && aposta.length === 5 && friosRestantes.length > 0) {
             // Pega o número mais crítico restante e verifica apenas as regras de distribuição
             const proximoFrio = friosRestantes.find(n => isEquilibrado(n.dezena, false));

             if (proximoFrio) {
                 numeroEscolhido = proximoFrio.dezena;
                 log.push(`- ${numeroEscolhido}: PREENCHIMENTO FINAL (Regra de Sequência RELAXADA)`);
             }
        }
        
        if (numeroEscolhido) {
            // Evita duplicar log se já foi adicionado
            if (!log.some(l => l.includes(numeroEscolhido))) { 
                 log.push(`- ${numeroEscolhido}: PREENCHIMENTO DE EQUILÍBRIO`);
            }
            aposta.push(numeroEscolhido);
        } else {
             break; 
        }
    }

    return { aposta: aposta.slice(0, 6), log };
}

// --- OPÇÃO 1: CICLO CRÍTICO (FRIO) ---
function gerarApostaCritico(numsData, data) {
    const pares = Object.entries(data.frequenciaPares).sort((a, b) => b[1] - a[1]);
    const numsCriticos = [...numsData].sort((a, b) => b.criticidade - a.criticidade);

    return criarAposta(
        numsCriticos, 
        pares, 
        [`Estratégia: Foco em CMR acima da média (CMR Crítico).`],
        "FRIO MÁXIMO (CMR Crítico)",
        "FRIO SECUNDÁRIO (Alto Atraso)"
    );
}

// --- OPÇÃO 2: FREQUÊNCIA PURA (QUENTE) ---
function gerarApostaQuente(numsData, data) {
    const pares = Object.entries(data.frequenciaPares).sort((a, b) => b[1] - a[1]);
    const numsQuentes = [...numsData].sort((a, b) => b.frequencia - a.frequencia);
    
    // Foco em números que saíram muito, permitindo 3 no mesmo setor (Alto Risco/Recompensa)
    return criarAposta(
        numsQuentes, 
        pares, 
        [`Estratégia: Máximo de números quentes e combinações históricas.`],
        "QUENTE MÁXIMO (Maior Frequência)",
        "QUENTE SECUNDÁRIO (Alta Frequência)",
        3, // Permite 3 por setor no quente
        3  // Permite 3 por dezena inicial no quente
    );
}

// --- OPÇÃO 3: ESTRATÉGIA MISTA (EQUILÍBRIO) ---
function gerarApostaMista(numsData, data) {
    // Ordena por números que estão mais próximos da média (CMR ~ 10.0, Criticidade ~ 0)
    const pares = Object.entries(data.frequenciaPares).sort((a, b) => b[1] - a[1]);
    const numsMistos = [...numsData].sort((a, b) => Math.abs(a.cmr - 10.0) - Math.abs(b.cmr - 10.0));

    return criarAposta(
        numsMistos, 
        pares, 
        [`Estratégia: Distribuição perfeita, escolhendo números com CMR próximo de 10.0 (neutros).`],
        "NEUTRO (CMR Perfeito)",
        "NEUTRO SECUNDÁRIO",
        2, // Rigidez no equilíbrio
        2
    );
}


// --- RENDERIZADOR GERAL PARA AS 3 APOSTAS ---
function renderAposta(resultado, idAposta, idEstrategia, limInf, limSup, badgeClass) {
    const apostaEl = document.getElementById(idAposta);
    const estrategiaEl = document.getElementById(idEstrategia);
    
    resultado.aposta.sort((a, b) => parseInt(a) - parseInt(b));
    const somaAposta = resultado.aposta.reduce((sum, d) => sum + parseInt(d), 0);
    
    let somaStatus = `<span class="highlight-green">SOMA VÁLIDA (${somaAposta})</span>`;
    if (somaAposta < limInf || somaAposta > limSup) {
         somaStatus = `<span class="highlight-red">SOMA FORA DA FAIXA (${somaAposta} | Ideal: ${limInf}-${limSup})</span>`;
    }

    apostaEl.innerHTML = resultado.aposta.map(dezena => 
        `<span class="badge ${badgeClass}" style="font-size: 1.2rem; padding: 8px;">${dezena}</span>`
    ).join('');
    
    estrategiaEl.innerHTML = `
        ${somaStatus}
        <ul class="list-unstyled small mt-2">${resultado.log.map(log => `<li>${log}</li>`).join('')}</ul>
    `;
}


// --- 1. RENDERIZAÇÃO DO RESUMO ESTATÍSTICO (Soma e Quadrantes) ---
function renderResumoEstatistico(data) {
    // A. Soma Ideal
    const somaMedia = data.somaMediaSorteio;
    const somaDP = data.desvioPadraoSoma;
    
    const limiteInferior = Math.round(somaMedia - somaDP);
    const limiteSuperior = Math.round(somaMedia + somaDP);
    
    document.getElementById('soma-ideal').textContent = `Entre ${limiteInferior} e ${limiteSuperior}`;
    document.getElementById('soma-media').textContent = somaMedia.toFixed(2);
    document.getElementById('soma-dp').textContent = somaDP.toFixed(2);

    // B. Distribuição por Quadrante
    const quadrantesEl = document.getElementById('distribuicao-quadrante');
    quadrantesEl.innerHTML = ''; 
    
    for (const [key, value] of Object.entries(data.mediaDistribuicaoSetores)) {
        const li = document.createElement('li');
        li.className = 'list-group-item list-group-item-custom';
        li.innerHTML = `<span>Média Setor ${key}:</span> <span class="highlight-green">${value.toFixed(2)} números</span>`;
        quadrantesEl.appendChild(li);
    }
}

// --- 4. RENDERIZAÇÃO DOS DÍGITOS FIM E INÍCIO (CORREÇÃO) ---
function renderDigitosFimEInicio(data) {
    const totalDezenas = data.totalConcursosAnalisados * 6;
    
    // 1. Dígito Final (0 a 9)
    const finalEl = document.getElementById('frequencia-digito-final');
    finalEl.innerHTML = '';
    
    const maxFinal = Math.max(...Object.values(data.frequenciaDigitoFinal));
    const mediaFinal = totalDezenas / 10;
    document.getElementById('media-digito-final').textContent = `Média esperada: ${mediaFinal.toFixed(1)}x.`;
    
    for (const [digito, freq] of Object.entries(data.frequenciaDigitoFinal).sort((a, b) => a[0] - b[0])) {
        const percent = (freq / maxFinal) * 100;
        const isFraco = freq < (mediaFinal * 0.95); 
        
        const itemHtml = `
            <div class="bar-item">
                <span class="bar-label">${digito} (Fim):</span>
                <div class="bar-fill" style="width: ${percent.toFixed(0)}%; background-color: ${isFraco ? '#dc3545' : '#007bff'};">
                    ${freq}x
                </div>
            </div>
        `;
        finalEl.insertAdjacentHTML('beforeend', itemHtml);
    }

    // 2. Dígito Inicial (0 a 5)
    const inicialEl = document.getElementById('frequencia-digito-inicial');
    inicialEl.innerHTML = '';
    
    const maxInicial = Math.max(...Object.values(data.frequenciaDigitoInicial));
    
    for (const [digito, freq] of Object.entries(data.frequenciaDigitoInicial).sort((a, b) => a[0] - b[0])) {
        let label = '';
        let mediaEsperada = 0;
        
        if (digito === '0') { label = '01-09'; mediaEsperada = totalDezenas * 9 / 60; }
        else if (digito === '5') { label = '50-60'; mediaEsperada = totalDezenas * 11 / 60; }
        else { label = `${digito}0-${digito}9`; mediaEsperada = totalDezenas * 10 / 60; }

        const percent = (freq / maxInicial) * 100;
        const isFraco = freq < (mediaEsperada * 0.95);
        
        const itemHtml = `
            <div class="bar-item">
                <span class="bar-label">${label}:</span>
                <div class="bar-fill" style="width: ${percent.toFixed(0)}%; background-color: ${isFraco ? '#dc3545' : '#007bff'};">
                    ${freq}x (Esperado: ${mediaEsperada.toFixed(0)})
                </div>
            </div>
        `;
        inicialEl.insertAdjacentHTML('beforeend', itemHtml);
    }
}


// --- 2. RENDERIZAÇÃO DE FREQUÊNCIA E ATRASO (CMR) ---
function renderFrequenciaEAtrazo(data) {
    const tableEl = document.getElementById('tabela-frequencia-atraso');
    tableEl.innerHTML = ''; 

    const estatisticas = [];
    for (let i = 1; i <= 60; i++) {
        const dezena = formatDezena(i);
        
        const cmr = data.cicloMedioRecorrencia[dezena] || 0;
        
        estatisticas.push({
            dezena: dezena,
            frequencia: data.frequenciaNumeros[dezena] || 0,
            atraso: data.atrasoNumeros[dezena] || 0,
            cmr: cmr
        });
    }

    // Ordena os números pelo maior atraso (decrescente)
    estatisticas.sort((a, b) => b.atraso - a.atraso);
    
    // Renderiza apenas os 10 mais atrasados
    estatisticas.slice(0, 10).forEach(item => {
        const cmrDiferenca = item.atraso - item.cmr;
        let cmrText = '';
        let cmrClass = '';
        
        // CRITICIDADE: Atraso atual maior que o ciclo médio histórico do próprio número
        if (item.atraso > 0 && cmrDiferenca > 0.5) { 
            cmrText = `(${cmrDiferenca.toFixed(1)} acima do CMR de ${item.cmr.toFixed(1)})`;
            cmrClass = 'highlight-red'; 
        } else if (item.atraso > 0 && cmrDiferenca < -0.5) {
             cmrText = `(Saída recente, CMR de ${item.cmr.toFixed(1)})`;
            cmrClass = 'highlight-green'; 
        } else {
            cmrText = `(CMR: ${item.cmr.toFixed(1)})`;
            cmrClass = 'text-muted';
        }

        const itemHtml = `
            <div class="list-group-item list-group-item-custom">
                <div>
                    <span class="badge bg-danger badge-numero">${item.dezena}</span>
                    <span class="ms-2">Atraso: <strong>${item.atraso} concursos</strong></span>
                </div>
                <div class="text-end">
                    <small class="${cmrClass}">${cmrText}</small><br>
                    <small class="text-muted">Freq: ${item.frequencia}x</small>
                </div>
            </div>
        `;
        tableEl.insertAdjacentHTML('beforeend', itemHtml);
    });
    
    // Adiciona um separador e os 5 mais frequentes (quentes)
    tableEl.insertAdjacentHTML('beforeend', '<div class="list-group-item bg-light text-center">--- Mais Frequentes (Quentes) ---</div>');
    
    estatisticas.sort((a, b) => b.frequencia - a.frequencia);
    estatisticas.slice(0, 5).forEach(item => {
        const itemHtml = `
            <div class="list-group-item list-group-item-custom list-group-item-success">
                <div>
                    <span class="badge bg-success badge-numero">${item.dezena}</span>
                    <span class="ms-2">Frequência: <strong>${item.frequencia}x</strong></span>
                </div>
                <div class="text-end">
                    <small class="text-muted">Atraso: ${item.atraso}</small>
                </div>
            </div>
        `;
        tableEl.insertAdjacentHTML('beforeend', itemHtml);
    });
}

// --- 3. RENDERIZAÇÃO DE PARES E TRINCAS ---
function renderParesETrincas(data) {
    // A. Pares
    const paresEl = document.getElementById('top-pares');
    paresEl.innerHTML = '';
    
    const topPares = Object.entries(data.frequenciaPares)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10);
        
    topPares.forEach(([par, freq]) => {
        const li = document.createElement('li');
        li.className = 'list-group-item list-group-item-custom';
        li.innerHTML = `<span>${par}</span> <span class="badge bg-warning text-dark">${freq}x</span>`;
        paresEl.appendChild(li);
    });

    // B. Trincas
    const trincasEl = document.getElementById('top-trincas');
    trincasEl.innerHTML = '';
    
    const topTrincas = Object.entries(data.frequenciaTrincas)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5);
        
    topTrincas.forEach(([trinca, freq]) => {
        const li = document.createElement('li');
        li.className = 'list-group-item list-group-item-custom';
        li.innerHTML = `<span>${trinca}</span> <span class="badge bg-danger">${freq}x</span>`;
        trincasEl.appendChild(li);
    });
}


// Inicia o carregamento dos dados quando a página é carregada
document.addEventListener('DOMContentLoaded', carregarEstatisticas);