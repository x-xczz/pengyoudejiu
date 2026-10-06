'use strict';
// 将下载的官方 Markdown 生成可直接双击的离线网页；原文与许可证不做修改。
// 参数可指定已安装的 marked 模块入口；正常开发环境也可直接使用本机的 marked。
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../docs/forge-1.20.1');
const source = path.join(root, 'docs');
const esc = value => value.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const unix = value => value.split(path.sep).join('/');
function walk(dir) {
  return fs.readdirSync(dir, {withFileTypes:true}).flatMap(item => item.isDirectory()
    ? walk(path.join(dir,item.name)) : item.name.endsWith('.md') ? [path.join(dir,item.name)] : []);
}
function admonitions(markdown) {
  const lines = markdown.split('\n'), output = [];
  for (let i=0; i<lines.length; i++) {
    const match = /^!!!\s+(\w+)(?:\s+"(.*)")?\s*$/.exec(lines[i]);
    if (!match) {output.push(lines[i]); continue;}
    output.push('> **' + (match[2] || match[1].toUpperCase()) + '**', '>');
    while (i+1<lines.length && (/^ {4}/.test(lines[i+1]) || !lines[i+1].trim())) {
      output.push('> ' + lines[++i].replace(/^ {4}/, ''));
    }
    output.push('');
  }
  return output.join('\n');
}
(async () => {
  const {marked} = await import(process.argv[2] ? pathToFileURL(path.resolve(process.argv[2])).href : 'marked');
  const pages = walk(source).sort().map(file => {
    const markdown = fs.readFileSync(file,'utf8');
    const title = marked.lexer(markdown).find(token => token.type === 'heading' && token.depth === 1)?.text || path.basename(file,'.md');
    return {file, markdown, title, html:file.replace(/\.md$/,'.html'), label:unix(path.relative(source,file))};
  });
  // 章节顺序沿用官方目录；用 Markdown 语法树读取标题，避免把代码注释误当成标题。
  const navigation = [...fs.readFileSync(path.join(root,'mkdocs.yml'),'utf8').matchAll(/:\s*'([^']+\.md)'/g)].map(match=>match[1]);
  const order = label => {const index=navigation.indexOf(label);return index<0?navigation.length:index};
  pages.sort((a,b)=>order(a.label)-order(b.label));
  const css = `*{box-sizing:border-box}[hidden]{display:none!important}body{margin:0;color:#253046;background:#f8fafc;font:16px/1.75 system-ui,"Microsoft YaHei",sans-serif}aside{position:fixed;inset:0 auto 0 0;width:285px;overflow:auto;background:#12243a;color:#e8f1ff;padding:24px 18px}aside a{display:block;color:#d4e4f5;text-decoration:none;padding:5px 8px;border-radius:5px;font-size:13px}aside a:hover,aside a.current{background:#254563;color:white}aside h2{font-size:18px;margin:0}aside small{color:#a9bfd7}input{width:100%;padding:10px;border-radius:6px;margin:16px 0;border:0;font:inherit}main{margin-left:285px;max-width:1150px;padding:35px 50px 70px}h1,h2,h3{line-height:1.3;scroll-margin-top:20px}h1{font-size:32px}h2{margin-top:40px;border-bottom:1px solid #dae1e9;padding-bottom:10px}a{color:#15659b}pre{background:#142134;color:#e2edfa;padding:18px;overflow:auto;border-radius:8px;font-size:14px;line-height:1.6}code{font-family:Consolas,monospace}p code,li code,td code{background:#e7edf4;padding:2px 4px;border-radius:3px;overflow-wrap:anywhere}blockquote{margin:20px 0;padding:5px 18px;border-left:4px solid #398bc0;background:#eaf3fa}table{border-collapse:collapse;display:block;overflow:auto}th,td{padding:9px 12px;border:1px solid #d7e0ea;text-align:left}img{max-width:100%}.meta,footer{font-size:13px;color:#607188}footer{margin-top:45px;padding-top:20px;border-top:1px solid #d7e0ea}@media(max-width:800px){aside{position:relative;width:100%;max-height:280px}main{margin:0;padding:24px}}`;
  fs.writeFileSync(path.join(root,'offline.css'),css);
  function shell(file,title,content,online) {
    const relative = target => unix(path.relative(path.dirname(file),target));
    const nav = pages.map(page => `<a ${page.html===file?'class="current" ':''}href="${esc(relative(page.html))}" title="${esc(page.label)}">${esc(page.title)}</a>`).join('\n');
    return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${esc(title)} · Forge 1.20.1</title><link rel="stylesheet" href="${relative(path.join(root,'offline.css'))}"></head><body><aside><h2>Forge 1.20.1</h2><small>官方文档 · 离线快照 · 70 篇</small><input id="filter" aria-label="筛选章节" placeholder="筛选章节名称…"><nav>${nav}</nav></aside><main><div class="meta"><a href="${relative(path.join(root,'index.html'))}">离线目录</a> · 版本 1.20.1 · 快照日期 2026-10-05${online?' · <a href="'+esc(online)+'">查看官方原文（联网）</a>':''}</div>${content}<footer>正文来自 MinecraftForge/Documentation 的 1.20.1 分支，英文原文保留。离线页面只调整排版、提示框与相对链接，不代表官方发布的新版本。<a href="${relative(path.join(root,'LICENSE'))}">原始许可证</a> · 页面内查找请按 Ctrl+F。外部链接仍需网络。</footer></main><script>document.getElementById('filter').addEventListener('input',function(){const q=this.value.toLowerCase();for(const a of document.querySelectorAll('nav a'))a.hidden=!(a.textContent+' '+a.title).toLowerCase().includes(q)});</script></body></html>`;
  }
  for(const page of pages) {
    const ids = new Map();
    let html = marked.parse(admonitions(page.markdown));
    html = html.replace(/<h([1-6])>([\s\S]*?)<\/h\1>/g, (_,level,text) => {
      const base=text.replace(/<[^>]*>/g,'').toLowerCase().replace(/&[^;]+;/g,'').replace(/[^\p{L}\p{N}_\s-]/gu,'').trim().replace(/\s+/g,'-');
      const count=ids.get(base)||0;ids.set(base,count+1);
      return `<h${level} id="${esc(base+(count?'-'+count:''))}">${text}</h${level}>`;
    });
    html = html.replace(/href="([^"#]*\.md)(#[^"]*)?"/g, (match,file,hash='') => /^(?:https?:|\/\/)/.test(file) ? match : `href="${file.slice(0,-3)}.html${hash}"`);
    // 上游 ambocc 误写成相对地址；只修复生成网页，保留原始 Markdown。
    html = html.replace('href="ambocc"', 'href="https://en.wikipedia.org/wiki/Ambient_occlusion"');
    const online='https://docs.minecraftforge.net/en/1.20.1/'+page.label.replace(/(?:^|\/)index\.md$/,m=>m.startsWith('/')?'/':'').replace(/\.md$/,'/');
    fs.writeFileSync(page.html,shell(page.html,page.title,html,online));
  }
  const landing=path.join(root,'index.html');
  const links=pages.map(p=>`<li><a href="${esc(unix(path.relative(root,p.html)))}">${esc(p.title)}</a> <small>${esc(p.label)}</small></li>`).join('\n');
  fs.writeFileSync(landing,shell(landing,'离线阅读目录',`<h1>Forge 1.20.1 离线文档</h1><p>已保存 <strong>${pages.length} 篇官方英文文档</strong>。无需联网、安装 MkDocs 或启动服务器，直接打开本页即可阅读。左侧支持按章节名筛选。</p><blockquote>本项目学习建议：Getting Started → Registries → Events → Sides → Sounds → SimpleImpl → Game Tests。先理解物品注册，再看声音资源、事件和客户端/服务端同步。</blockquote><p>原始 Markdown 与图片保留在 docs 子目录，原始 mkdocs.yml、主题与 LICENSE 也已保存。以后分析本项目时，可直接查阅这份 1.20.1 文档，不要套用新版本 API。</p><ol>${links}</ol>`));
  const archive=path.resolve(__dirname,'../build/docs-download/forge-1.20.1.zip');
  fs.writeFileSync(path.join(root,'snapshot.json'),JSON.stringify({version:'1.20.1',snapshotDate:'2026-10-05',pageCount:pages.length,website:'https://docs.minecraftforge.net/en/1.20.1/',source:'https://github.com/MinecraftForge/Documentation/tree/1.20.1',archiveUrl:'https://codeload.github.com/MinecraftForge/Documentation/zip/refs/heads/1.20.1',archiveSha256:crypto.createHash('sha256').update(fs.readFileSync(archive)).digest('hex'),format:'Original Markdown plus locally generated static HTML'},null,2)+'\n');
  console.log(`Generated ${pages.length} offline pages and index: ${landing}`);
})().catch(error=>{console.error(error);process.exitCode=1});
