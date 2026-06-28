window.Modal={
 open(id){const e=document.getElementById(id);if(e)e.style.display='flex';},
 close(id){const e=document.getElementById(id);if(e)e.style.display='none';}
};
document.addEventListener('keydown',e=>{
 if(e.key==='Escape'){
  document.querySelectorAll('.d-modal').forEach(m=>m.style.display='none');
 }
});
