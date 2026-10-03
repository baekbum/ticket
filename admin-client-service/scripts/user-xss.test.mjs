import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';

const code = await readFile(new URL('../legacy-source/static/js/user/fragment-user.js', import.meta.url), 'utf8');

// Record DOM writes and callbacks so untrusted values cannot silently move back into HTML/code sinks.
class Element {
  constructor() {
    this.children = [];
    this.nodes = new Map();
    this.listeners = new Map();
    this.dataset = {};
    this.style = {};
    this.value = '20';
    this.classes = new Set();
    this.classList = {
      add: value => this.classes.add(value),
      remove: value => this.classes.delete(value),
    };
  }
  set innerHTML(value) {
    this.html = value;
    this.children = [];
    this.nodes.clear();
    // The table renderer uses nine fixed cells. This harness does not execute HTML.
    if (this.tag === 'tr') this.cells = Array.from({ length: 9 }, () => new Element());
  }
  get innerHTML() { return this.html || ''; }
  setAttribute(name, value) { this[name] = value; }
  appendChild(child) { this.children.push(child); }
  replaceChildren() { this.children = []; }
  querySelector(selector) {
    if (selector.startsWith('.address-') && !this.html?.includes(selector.slice(1))) return null;
    if (!this.nodes.has(selector)) this.nodes.set(selector, new Element());
    return this.nodes.get(selector);
  }
  addEventListener(type, callback) { this.listeners.set(type, callback); }
  click(event = { stopPropagation() {} }) { this.listeners.get('click')?.(event); }
}

async function loadScreen(users, addresses = []) {
  const elements = new Map();
  const document = {
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, new Element());
      return elements.get(id);
    },
    querySelectorAll: () => [],
    createElement(tag) { const element = new Element(); element.tag = tag; return element; },
  };
  const window = { Pagination: { register() {} } };
  const errors = [];
  const Fetch = async url => ({
    ok: true,
    json: async () => ({ content: url.includes('/address/') ? addresses : users, totalPages: 1 }),
  });
  Object.assign(window, {
    document, window, Fetch, localStorage: { getItem: () => null },
    showToast: (...args) => errors.push(args),
  });
  vm.runInNewContext(code, window);
  await window.loadUserList();
  assert.deepEqual(errors, []);
  return { window, document, errors };
}

test('회원 입력은 HTML이 아닌 원문 텍스트로 표시한다', async () => {
  const payload = '<img src=x onerror="window.__xss=true"> & " \' <svg onload="window.__xss=true">';
  const user = { id: 7, userId: payload, name: payload, email: payload, grade: payload };
  const { document } = await loadScreen([user]);
  const row = document.getElementById('user-table-body').children[0];
  assert.equal(row.cells[2].querySelector('strong').textContent, payload);
  assert.equal(row.cells[3].textContent, payload);
  assert.equal(row.cells[4].textContent, payload);
  assert.equal(row.querySelector('.badge-grade').textContent, payload);
  assert.ok(row.querySelector('.badge-grade').classes.has('badge-grade-general'));
  assert.doesNotMatch(row.innerHTML, /<img|<svg|onerror|onload|onclick=/);
});

test('회원 버튼은 사용자 값을 코드로 해석하지 않고 콜백 인자로 전달한다', async () => {
  const user = { id: "7');window.__xss=true;//", userId: "a'\"<b>&", name: '회원', email: null, grade: 'VIP' };
  const { document, window } = await loadScreen([user]);
  const row = document.getElementById('user-table-body').children[0];
  assert.doesNotMatch(row.innerHTML, /__xss|onclick=/);
  assert.equal(row.cells[4].textContent, '');
  assert.ok(row.querySelector('.badge-grade').classes.has('badge-grade-vip'));
  const calls = [];
  for (const name of ['openUserAddressModal', 'openModalForUpdate', 'openConfirmModalFromRow', 'openModalForView', 'toggleRowCheckbox']) {
    window[name] = (...args) => calls.push([name, ...args]);
  }
  row.querySelector('.btn-address').click();
  row.querySelector('.btn-outline').click();
  row.querySelector('.btn-danger').click();
  row.click();
  const checkbox = row.querySelector('.row-checkbox');
  checkbox.click();
  assert.deepEqual(calls, [
    ['openUserAddressModal', user.userId], ['openModalForUpdate', user.id],
    ['openConfirmModalFromRow', user.id], ['openModalForView', user.id],
    ['toggleRowCheckbox', checkbox, user.id],
  ]);
  let stopped = 0;
  row.cells[0].click({ stopPropagation() { stopped++; } });
  row.querySelector('.actions').click({ stopPropagation() { stopped++; } });
  assert.equal(stopped, 2);
});

test('배송지 텍스트를 이스케이프하고 버튼의 동적 인라인 이벤트를 제거한다', async () => {
  const payload = '<img src=x onerror="window.__xss=true">';
  const address = {
    addressId: '1);window.__xss=true;//', alias: payload, recipientName: payload,
    recipientPhone: payload, zipCode: payload, address: payload, detailAddress: payload,
    status: payload, defaultAddress: false,
  };
  const { document, window, errors } = await loadScreen([{ id: 7, userId: 'member' }], [address]);
  await window.openUserAddressModal('member');
  assert.deepEqual(errors, []);
  const card = document.getElementById('address-card-list').children[0];
  assert.doesNotMatch(card.innerHTML, /<img|onclick=/);
  assert.match(card.innerHTML, /&lt;img src=x onerror=&quot;window.__xss=true&quot;&gt;/);
  const calls = [];
  window.openUserAddressEditModal = id => calls.push(['edit', id]);
  window.setDefaultUserAddress = id => calls.push(['default', id]);
  window.disableUserAddress = id => calls.push(['disable', id]);
  card.querySelector('.address-edit').click();
  card.querySelector('.address-set-default').click();
  card.querySelector('.address-disable').click();
  assert.deepEqual(calls, [['edit', address.addressId], ['default', address.addressId], ['disable', address.addressId]]);
});
