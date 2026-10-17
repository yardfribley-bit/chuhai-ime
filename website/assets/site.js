'use strict';
// 官网交互示意只替换页面上的示例句子，不调用翻译服务，也不读取访客输入。
const accept = document.getElementById('demo-accept');
const reset = document.getElementById('demo-reset');
const text = document.getElementById('demo-text');
const status = document.getElementById('demo-status');
const translation = document.querySelector('.translation');
accept.addEventListener('click', () => {
  text.innerHTML = 'Windows 桌面版 (Claude 桌面版)';
  translation.hidden = true;
  reset.hidden = false;
  status.textContent = '已用中文替换选区';
});
reset.addEventListener('click', () => {
  text.innerHTML = 'Windows 桌面版 (<mark>Claude Desktop</mark>)';
  translation.hidden = false;
  reset.hidden = true;
  status.textContent = '小窗出现后再按回车';
});
