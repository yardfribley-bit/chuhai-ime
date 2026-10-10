'use strict';
// 官网交互示意仅替换展示文案，不调用翻译服务或读取访客输入。
const accept = document.getElementById('demo-accept');
const reset = document.getElementById('demo-reset');
const text = document.getElementById('demo-text');
const status = document.getElementById('demo-status');
const translation = document.querySelector('.translation');
const candidate = document.querySelector('.candidate');
accept.addEventListener('click', () => {
  text.textContent = 'I discovered a new security vulnerability today and would like to share it with you.';
  translation.hidden = true;
  candidate.hidden = true;
  reset.hidden = false;
  status.textContent = '已确认英文 · 交互示意';
});
reset.addEventListener('click', () => {
  text.textContent = '今天发现了一个新的安全漏洞，希望和大家分享。';
  translation.hidden = false;
  candidate.hidden = false;
  reset.hidden = true;
  status.textContent = '输入 //，预览后确认';
});
