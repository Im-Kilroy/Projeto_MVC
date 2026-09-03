const icons = { 'Pão':'🥖', 'Macarrão':'🍝', 'Café':'☕', 'Cerveja':'🍺', 'Carne':'🥩', 'Morango':'🍓' };
const labels = { longe:'Validade segura', proximo:'Até 3 meses', vencido:'Vencido' };
let products = [];
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
async function load() {
  const response = await fetch('/api/mercadorias?busca=' + encodeURIComponent(document.querySelector('#search').value));
  const data = await response.json(); products = data.mercadorias;
  document.querySelector('#today').textContent = new Date(data.hoje + 'T00:00:00').toLocaleDateString('pt-BR');
  document.querySelector('#total').textContent = products.length;
  document.querySelector('#available').textContent = products.filter(p => p.status).length;
  document.querySelector('#empty').hidden = products.length > 0;
  document.querySelector('#grid').innerHTML = products.map(p => `<button class="card" data-id="${p.id}"><div class="image">${icons[p.nome] || '📦'}</div><div class="content"><div class="heading"><h2>${escapeHtml(p.nome)}</h2><span class="${p.validade_status}">● ${labels[p.validade_status]}</span></div><div class="info"><span>Quantidade<strong>${p.quantidade_produtos} un.</strong></span><span>Validade<strong class="validity-value ${p.validade_status}">${escapeHtml(p.data_validade)}</strong></span></div><footer>Responsável: <b>${escapeHtml(p.responsavel)}</b><em>Ver dashboard →</em></footer></div></button>`).join('');
  document.querySelectorAll('.card').forEach(card => card.onclick = () => show(Number(card.dataset.id)));
}
function show(id) {
  const p = products.find(item => item.id === id); if (!p) return;
  document.querySelector('#detail-icon').textContent = icons[p.nome] || '📦'; document.querySelector('#detail-name').textContent = p.nome;
  document.querySelector('#detail-status').textContent = p.status_label; document.querySelector('#detail-status').className = 'badge ' + (p.status ? 'available' : 'unavailable');
  document.querySelector('#detail-id').textContent = '#' + String(p.id).padStart(2, '0'); document.querySelector('#detail-quantity').textContent = p.quantidade_produtos + ' unidades';
  document.querySelector('#detail-fabrication').textContent = p.data_fabricacao; document.querySelector('#detail-expiration').textContent = p.data_validade; document.querySelector('#detail-owner').textContent = p.responsavel;
  document.querySelector('#detail-validity').textContent = labels[p.validade_status]; document.querySelector('#detail-validity').className = p.validade_status;
  document.querySelector('#product-modal').hidden = false;
}
document.querySelector('#search').oninput = load;
document.querySelectorAll('[data-close]').forEach(button => button.onclick = () => button.closest('.modal').hidden = true);
document.querySelector('#pdf-button').onclick = async () => {
  try {
    const response = await fetch('/api/relatorio-pdf');
    if (!response.ok) throw new Error('Não foi possível gerar o PDF.');
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'relatorio-estoque.pdf';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  } catch (error) {
    window.alert(error.message);
  }
};
const openAgentModal = (label, greeting) => {
  document.querySelector('#agent-label').textContent = label;
  const answer = document.querySelector('#answer');
  if (greeting) answer.textContent = greeting;
  const modal = document.querySelector('#agent-modal');
  modal.hidden = false;
  window.requestAnimationFrame(() => document.querySelector('#question').focus());
};

document.querySelector('#gemini-button').onclick = () => {
  openAgentModal('RENATINHA IA', 'Olá, sou a Renatinha IA, sua amiguinha, o que deseja saber sobre o estoque?');
};
document.querySelector('#ask').onclick = ask;
document.querySelector('#question').onkeydown = event => {
  if (event.key === 'Enter') {
    event.preventDefault();
    ask();
  }
};
async function ask() {
  const input = document.querySelector('#question');
  const question = input.value.trim();
  if (!question) return;
  input.disabled = true;
  document.querySelector('#answer').textContent = 'Consultando o estoque...';
  try {
    const response = await fetch('/api/agente', { method:'POST', headers:{'Content-Type':'text/plain; charset=UTF-8'}, body:question });
    if (!response.ok) throw new Error('Não foi possível consultar o agente.');
    const data = await response.json();
    document.querySelector('#answer').textContent = data.resposta;
  } catch (error) {
    document.querySelector('#answer').textContent = error.message;
  } finally {
    input.value = '';
    input.disabled = false;
    input.focus();
  }
}
const initialAnswer = 'Olá, sou a Renatinha IA, sua amiguinha, o que deseja saber sobre o estoque?';
document.querySelector('#answer').textContent = initialAnswer;
load();
