
window.showToast=function(msg,isErr){
 const t=document.getElementById('toast');
 if(!t) return;
 t.textContent=msg;
 t.style.background=isErr?'#A32D2D':'#3C3489';
 t.classList.add('show');
 setTimeout(()=>t.classList.remove('show'),2500);
};
