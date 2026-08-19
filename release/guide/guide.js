const order = ['sr', 'sl', 'en'];
function pick(l) {
  document.querySelectorAll('.lang').forEach(s => s.classList.toggle('on', s.dataset.lang === l));
  document.querySelectorAll('.tab').forEach(t => t.setAttribute('aria-current', String(t.dataset.go === l)));
  document.documentElement.lang = l;
  try { localStorage.setItem('deda_guide_lang', l); } catch (e) {}
}
document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => pick(t.dataset.go)));
let start = 'sr';
try { const s = localStorage.getItem('deda_guide_lang'); if (order.includes(s)) start = s; } catch (e) {}
const nav = (navigator.language || '').slice(0, 2);
let saved = null;
try { saved = localStorage.getItem('deda_guide_lang'); } catch (e) {}
if (!saved && order.includes(nav)) start = nav;
pick(start);
