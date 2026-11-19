import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import ts from 'typescript';
import { JSDOM } from 'jsdom';
import React, { act } from 'react';

const dom = new JSDOM('<div id="root"></div>', { url: 'https://localhost/?page=myTicket' });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.sessionStorage = dom.window.sessionStorage;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.CustomEvent = dom.window.CustomEvent;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
dom.window.scrollTo = () => {};
dom.window.alert = () => {};
dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };
const { createRoot } = await import('react-dom/client');
let appInstance = 0;

// 실제 App과 화면들을 불러오되 CSS만 제외한다. 브라우저·서버 세션에는 접근하지 않는다.
function appModule() {
  const cache = new Map();
  function compile(file) {
    if (cache.has(file)) return cache.get(file);
    let source = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
    }).outputText.replace(/import ['"][^'"]+\.css['"];?/g, '');
    source = source.replace(/from (['"])([^'"]+)\1/g, (_match, _quote, specifier) => {
      if (!specifier.startsWith('.')) return `from '${import.meta.resolve(specifier)}'`;
      const base = path.resolve(path.dirname(file), specifier);
      const dependency = [base, base + '.ts', base + '.tsx'].find((candidate) => fs.existsSync(candidate));
      if (!dependency) throw new Error('Missing dependency: ' + specifier);
      return `from '${compile(dependency)}'`;
    });
    const url = 'data:text/javascript;base64,' + Buffer.from(source).toString('base64');
    cache.set(file, url);
    return url;
  }
  return compile(fileURLToPath(new URL('../src/App.tsx', import.meta.url))) + '#case-' + appInstance++;
}

test('마이티켓 인증 실패 시 사용자명을 지우고 로그인 화면과 만료 안내를 표시한다', async () => {
  for (const missingTokens of [false, true]) {
    window.history.replaceState({}, '', '/?page=myTicket');
    sessionStorage.clear();
    sessionStorage.setItem('ticksy.userName', '아이유');
    sessionStorage.setItem('ticksy.activeToken.1', 'active');
    if (!missingTokens) {
      sessionStorage.setItem('ticksy.accessToken', 'expired');
      sessionStorage.setItem('ticksy.refreshToken', 'rejected');
    }
    window.fetch = async (_url, options) => {
      if (options?.signal?.aborted) throw new DOMException('Aborted', 'AbortError');
      return new Response('{}', { status: 401 });
    };
    const App = (await import(appModule())).default;
    const root = createRoot(document.getElementById('root'));
    try {
      await act(async () => { root.render(React.createElement(App)); });
      assert.equal(new URLSearchParams(window.location.search).get('page'), 'login', `missingTokens=${missingTokens}`);
      assert.equal(sessionStorage.getItem('ticksy.userName'), null);
      assert.equal(sessionStorage.getItem('ticksy.accessToken'), null);
      assert.equal(sessionStorage.getItem('ticksy.activeToken.1'), 'active');
      assert.ok(document.querySelector('input[type="password"]'), 'Login form must be shown');
      assert.match(document.body.textContent, /세션이 만료되었습니다\. 다시 로그인해주세요\./);
      assert.doesNotMatch(document.body.textContent, /아이유님 환영합니다/);
    } finally {
      await act(async () => { root.unmount(); });
    }
  }
});
