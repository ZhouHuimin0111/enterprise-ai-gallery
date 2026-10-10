'use strict';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const bytes = n => n >= 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${(n / 1024).toFixed(1)} KB`;
const state = {token:sessionStorage.getItem('framehub-token'),user:null,tab:'public',space:null,publicSpace:null,spaces:[],mode:null,page:1,total:0,pictures:[],selected:new Set(),urls:[],socket:null,edit:null,dirty:false,load:0};
const titles = {public:'公共图库',private:'私有图库',team:'团队空间',ai:'AI 创作室',analytics:'空间分析',admin:'审核管理'};
const roles = {OWNER:'所有者',ADMIN:'管理员',EDITOR:'编辑者',VIEWER:'浏览者',GUEST:'访客'};
const statuses = {QUEUED:'排队中',SUBMITTING:'正在提交',SUBMITTED:'生成中',SUCCEEDED:'已完成',FAILED:'失败',UNKNOWN:'提交结果待核查',PENDING:'待审核',APPROVED:'已通过',REJECTED:'已拒绝'};
let toastTimer,jobTimer,searchTimer;
function toast(message) { $('#toast').textContent=message;$('#toast').classList.remove('hidden');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.add('hidden'),4200); }
async function api(path,options={}) {
  const headers={...(state.token?{Authorization:`Bearer ${state.token}`}:{})};
  if(options.body && !(options.body instanceof FormData)) { headers['Content-Type']='application/json';options.body=JSON.stringify(options.body); }
  const response=await fetch(path,{...options,headers:{...headers,...options.headers}});
  let result;try { result=await response.json(); } catch { throw new Error('服务连接异常，请稍后重试'); }
  if(!response.ok || result.code!=='OK') {
    if(response.status===401 && state.token) { state.token=null;state.user=null;sessionStorage.removeItem('framehub-token');updateUser(); }
    const error=new Error(result.message||'操作未成功');error.code=result.code;throw error;
  }return result.data;
}
async function action(work) { try { return await work(); } catch(e) { toast(e.message); } }
function modal(title,html) { closeModal();$('#modal-title').textContent=title;$('#modal-body').innerHTML=html;$('#modal').showModal(); }
function closeModal() { state.socket?.close();state.socket=null;state.edit=null;state.dirty=false;if($('#modal').open) $('#modal').close(); }
$('#close-modal').addEventListener('click',closeModal);$('#modal').addEventListener('cancel',()=>{state.socket?.close();state.socket=null;});
function updateUser() { $('#auth-button').textContent=state.user?`${state.user.displayName} · 退出`:'登录 / 注册';$('#admin-nav').classList.toggle('hidden',state.user?.role!=='ADMIN'); }
function requireUser() { if(state.user) return true;authModal();return false; }
function authModal(register=false) {
  modal(register?'创建你的创作账号':'欢迎回到 FrameHub',`<div class="tabs"><button id="login-tab" class="button ${register?'secondary':''}">登录</button><button id="register-tab" class="button ${register?'':'secondary'}">注册</button></div><form id="auth-form" class="form">${register?'<label>显示名称<input name="displayName" required maxlength="60" placeholder="你希望大家怎么称呼你"></label>':''}<label>用户名<input name="username" required minlength="3" maxlength="32" pattern="[a-zA-Z0-9_]{3,32}" autocomplete="username" placeholder="3–32 位字母、数字或下划线"></label><label>密码<input name="password" type="password" required minlength="8" maxlength="64" autocomplete="${register?'new-password':'current-password'}" placeholder="至少 8 位"></label><p id="auth-error" class="error hidden"></p><button class="button" type="submit">${register?'注册并登录':'登录'}</button><small>账号用于管理私有素材、参与团队与保存创作任务。</small></form>`);
  $('#login-tab').onclick=()=>authModal(false);$('#register-tab').onclick=()=>authModal(true);
  $('#auth-form').onsubmit=async e=> {
    e.preventDefault();const form=e.currentTarget,values=Object.fromEntries(new FormData(form));const button=$('button[type="submit"]',form);button.disabled=true;
    try {
      if(register) await api('/api/auth/register',{method:'POST',body:values});
      const data=await api('/api/auth/login',{method:'POST',body:{username:values.username,password:values.password}});
      state.token=data.token;state.user=data.user;sessionStorage.setItem('framehub-token',data.token);updateUser();closeModal();toast('登录成功');await loadSpaces();await navigate(state.tab);
    } catch(error) { $('#auth-error').textContent=error.message;$('#auth-error').classList.remove('hidden'); }finally { button.disabled=false; }
  };
}
$('#auth-button').onclick=()=>action(async()=> {
  if(!state.user) return authModal();await api('/api/auth/logout',{method:'POST'});state.token=null;state.user=null;state.spaces=[];sessionStorage.removeItem('framehub-token');closeModal();updateUser();toast('已退出登录');await navigate('public');
});
async function loadSpaces() { state.spaces=state.user?await api('/api/spaces'):[]; }
const convertSpace = row => ({id:row.id,name:row.name,type:row.type,ownerId:row.owner_id,maxBytes:row.max_bytes,maxCount:row.max_count,usedBytes:row.used_bytes,usedCount:row.used_count,myRole:row.my_role});
function spaceOptions() { return [state.publicSpace,...state.spaces.map(convertSpace)].filter(Boolean).filter(s=>s.type==='PUBLIC'||['OWNER','ADMIN','EDITOR'].includes(s.myRole)).map(s=>`<option value="${s.id}">${esc(s.name)} · ${s.type==='PUBLIC'?'公开':s.type==='PRIVATE'?'私有':'团队'}</option>`).join(''); }
async function chooseSpace(id) { const data=await api(`/api/spaces/${id}`);state.space={...data.space,myRole:data.myRole};return state.space; }
function setHero(tab) {
  $('#breadcrumb').textContent=titles[tab];$$('[data-nav]').forEach(b=>b.classList.toggle('active',b.dataset.nav===tab));
  const content={public:['收集灵感，<br>创造更多可能<span>。</span>','探索、收藏与共享视觉素材，让团队创作从这里开始。','发现你的下一份灵感'],private:['你的创作，<br>有自己的空间<span>。</span>','只属于你的素材库，分类、检索与编辑井然有序。','管理你的私有素材'],team:['一起创作，<br>让灵感彼此连接<span>。</span>','为团队共享素材，通过角色与协作让每一次修改都有迹可循。','团队共享素材'],ai:['用语言描绘，<br>让想法成为画面<span>。</span>','描述你的创意，保存生成任务，将结果直接归档到图库。','AI 创作任务'],analytics:['看见素材，<br>也看见它的价值<span>。</span>','查看空间用量、内容分类和成员贡献，让素材管理更清晰。','空间分析'],admin:['认真审核，<br>让分享值得信任<span>。</span>','检查公共图片的内容与描述，维护清晰、有序的灵感图库。','待审核图片']}[tab];
  $('#page-title').innerHTML=content[0];$('#page-description').textContent=content[1];$('#gallery-title').textContent=content[2];
  $('#primary-action').textContent=tab==='team'?'＋ 创建团队空间':'＋ 上传图片';$('#secondary-action').textContent=tab==='team'?'↗ 加入已有团队':'✧ 开始 AI 创作';
  $('#mine-label').classList.toggle('hidden',tab!=='public');$('#search-form').classList.toggle('hidden',tab==='admin');
}
async function navigate(tab,spaceId=null) {
  state.tab=tab;state.page=1;state.selected.clear();closeModal();clearInterval(jobTimer);setHero(tab);
  $('#space-panel').classList.add('hidden');$('#extra-section').classList.add('hidden');$('#gallery-section').classList.remove('hidden');
  if(tab==='public') { state.space=state.publicSpace;await loadGallery(); }
  else if(!state.user) { $('#gallery').innerHTML='<div class="empty-state"><strong>登录后开启你的工作空间</strong>私有图库、团队素材和创作任务都将跟随你的账号。</div>';$('#picture-count').textContent='';$('.pagination').classList.add('hidden'); }
  else if(tab==='private') {
    await loadSpaces();const existing=state.spaces.find(s=>s.type==='PRIVATE');
    if(!existing) { $('#gallery').innerHTML='<div class="empty-state"><strong>为你的灵感留一个位置</strong>开通 100 MB 私有空间，开始上传和管理图片。<br><button id="create-private" class="button">创建私有空间</button></div>';$('#create-private').onclick=()=>createSpaceModal('PRIVATE');$('#picture-count').textContent='尚未创建';$('.pagination').classList.add('hidden'); }
    else { await chooseSpace(existing.id);renderSpacePanel();await loadGallery(); }
  } else if(tab==='team') {
    await loadSpaces();
    if(spaceId) { await chooseSpace(spaceId);renderSpacePanel();await loadGallery(); }
    else renderTeams();
  } else if(tab==='ai') { await loadSpaces();renderAi(); }
  else if(tab==='analytics') { await loadSpaces();renderAnalytics(spaceId); }
  else if(tab==='admin') { if(state.user.role!=='ADMIN') throw new Error('仅管理员可进行审核');state.space=state.publicSpace;await loadGallery(); }
}
$$('[data-nav]').forEach(b=>b.onclick=()=>action(()=>navigate(b.dataset.nav)));
$('#primary-action').onclick=()=> { if(!requireUser()) return;state.tab==='team'?createSpaceModal('TEAM'):uploadModal(); };
$('#secondary-action').onclick=()=>state.tab==='team'?acceptInviteModal():action(()=>navigate('ai'));
function renderSpacePanel() {
  const s=state.space;$('#space-panel').classList.remove('hidden');
  $('#space-panel').innerHTML=`<div class="panel space-summary"><div><h2>${esc(s.name)} <span class="status">${roles[s.myRole]||s.myRole}</span></h2><p>${s.usedCount} / ${s.maxCount} 张 · ${bytes(s.usedBytes)} / ${bytes(s.maxBytes)}<br>${s.type==='TEAM'?'成员共享素材，编辑使用版本校验并实时同步。':'此空间仅你可见。'}</p></div><div class="space-actions">${s.type==='TEAM'?'<button id="team-members" class="button secondary small">成员与邀请</button><button id="team-back" class="text-button">全部团队</button>':''}<button id="space-analysis" class="button secondary small">查看分析</button><button id="space-rename" class="text-button">重命名</button></div></div>`;
  $('#space-analysis').onclick=()=>action(()=>navigate('analytics',s.id));$('#space-rename').onclick=()=>renameModal();
  if(s.type==='TEAM') { $('#team-members').onclick=()=>action(()=>membersModal());$('#team-back').onclick=()=>action(()=>navigate('team')); }
}
function renderTeams() {
  $('#gallery-section').classList.add('hidden');$('#extra-section').classList.remove('hidden');const teams=state.spaces.filter(s=>s.type==='TEAM');
  $('#extra-section').innerHTML=`<div class="section-heading"><h2>我的团队空间</h2><span class="muted">${teams.length} 个团队</span></div><div class="space-grid">${teams.map(s=>`<article class="space-card"><p class="eyebrow">SHARED WORKSPACE</p><h3>${esc(s.name)}</h3><span class="status">${roles[s.my_role]}</span><p>${s.used_count} 张素材 · ${bytes(s.used_bytes)} / ${bytes(s.max_bytes)}</p><button class="button secondary small enter-team" data-id="${s.id}">进入空间 ↗</button></article>`).join('')||'<div class="empty-state"><strong>让团队拥有共同的素材库</strong>创建团队，或输入邀请代码加入已有空间。</div>'}</div>`;
  $$('.enter-team').forEach(b=>b.onclick=()=>action(()=>navigate('team',Number(b.dataset.id))));
}
function createSpaceModal(type) {
  if(!requireUser()) return;modal(type==='TEAM'?'创建团队空间':'创建私有空间',`<form id="space-form" class="form"><label>空间名称<input name="name" required maxlength="80" placeholder="${type==='TEAM'?'例如：品牌创意团队':'例如：我的灵感库'}"></label><small>${type==='TEAM'?'团队空间提供 500 MB 容量，可邀请管理员、编辑者和浏览者。':'私有空间提供 100 MB 容量，每个账号可创建一个。'}</small><button class="button">创建空间</button></form>`);
  $('#space-form').onsubmit=e=> { e.preventDefault();action(async()=> { const s=await api('/api/spaces',{method:'POST',body:{name:new FormData(e.target).get('name'),type}});closeModal();toast('空间已创建');await navigate(type==='TEAM'?'team':'private',s.id); }); };
}
function renameModal() {
  const s=state.space;modal('重命名空间',`<form id="rename-form" class="form"><label>名称<input name="name" required maxlength="80" value="${esc(s.name)}"></label><button class="button">保存</button></form>`);
  $('#rename-form').onsubmit=e=>{e.preventDefault();action(async()=>{await api(`/api/spaces/${s.id}`,{method:'PUT',body:{name:new FormData(e.target).get('name')}});closeModal();await navigate(s.type==='TEAM'?'team':'private',s.id);});};
}
$('#accept-invite').onclick=acceptInviteModal;
function acceptInviteModal() {
  if(!requireUser()) return;modal('加入团队空间','<form id="accept-form" class="form"><label>邀请代码<input name="token" required minlength="43" maxlength="43" placeholder="粘贴团队管理员提供的邀请代码"></label><small>每个邀请仅可使用一次，有效期为 48 小时。</small><button class="button">接受邀请</button></form>');
  $('#accept-form').onsubmit=e=>{e.preventDefault();action(async()=>{const s=await api('/api/spaces/accept-invite',{method:'POST',body:{token:new FormData(e.target).get('token').trim()}});closeModal();toast('已加入团队');await navigate('team',s.id);});};
}
async function membersModal() {
  const s=state.space,members=await api(`/api/spaces/${s.id}/members`),manager=['OWNER','ADMIN'].includes(s.myRole);
  modal('团队成员与邀请',`<p class="hint">${esc(s.name)} · ${members.length} 位成员</p><div>${members.map(m=>`<div class="member"><span class="name">${esc(m.display_name)}<br><small class="muted">${esc(m.username)}</small></span><span class="status">${roles[m.role]}</span>${manager&&m.role!=='OWNER'&&(s.myRole==='OWNER'||m.role!=='ADMIN')?`<select aria-label="调整 ${esc(m.display_name)} 角色" data-member="${m.user_id}">${(s.myRole==='OWNER'?['ADMIN','EDITOR','VIEWER']:['EDITOR','VIEWER']).map(r=>`<option value="${r}" ${r===m.role?'selected':''}>${roles[r]}</option>`).join('')}</select><button class="text-button danger remove-member" data-id="${m.user_id}">移除</button>`:''}</div>`).join('')}</div>${manager?`<h3 class="subheading">生成一次性邀请</h3><form id="invite-form" class="form"><label>成员角色<select name="role">${(s.myRole==='OWNER'?['EDITOR','VIEWER','ADMIN']:['EDITOR','VIEWER']).map(r=>`<option value="${r}">${roles[r]}</option>`).join('')}</select></label><button class="button">生成邀请代码</button></form><div id="invite-output"></div>`:''}`);
  $$('[data-member]').forEach(select=>select.onchange=()=>action(async()=>{await api(`/api/spaces/${s.id}/members/${select.dataset.member}`,{method:'PUT',body:{role:select.value}});toast('角色已更新');await membersModal();}));
  $$('.remove-member').forEach(b=>b.onclick=()=>action(async()=>{await api(`/api/spaces/${s.id}/members/${b.dataset.id}`,{method:'DELETE'});toast('成员已移除');await membersModal();}));
  if(manager) $('#invite-form').onsubmit=e=>{e.preventDefault();action(async()=>{const data=await api(`/api/spaces/${s.id}/invites`,{method:'POST',body:{role:new FormData(e.target).get('role')}});$('#invite-output').innerHTML=`<div class="token-output"><strong>邀请代码 · ${roles[data.role]}</strong><br>${esc(data.token)}<br><small>请手动复制并交给受邀成员；48 小时内可使用一次。</small></div>`;});};
}
async function loadGallery() {
  const generation=++state.load;state.selected.clear();updateBatch();$('#gallery').innerHTML='<div class="empty-state">正在整理素材…</div>';$('.pagination').classList.remove('hidden');
  const params=new URLSearchParams({spaceId:state.space.id,page:state.page,size:24,q:$('#search').value,category:$('#category-filter').value,tag:$('#tag-filter').value,sort:$('#sort-filter').value,mine:state.tab==='public'&&$('#mine-filter').checked});
  const result=await api(state.tab==='admin'?`/api/pictures/review-queue?page=${state.page}`:`/api/pictures?${params}`);if(generation!==state.load) return;
  state.pictures=result.records;state.total=result.total;state.urls.forEach(URL.revokeObjectURL);state.urls=[];
  $('#picture-count').textContent=`${result.total} 张${state.tab==='admin'?'待审核图片':'素材'}`;
  $('#gallery').innerHTML=result.records.map(p=>`<article class="picture-card" data-id="${p.id}">${canEdit(p)?`<input type="checkbox" class="select-picture" aria-label="选择 ${esc(p.title)}" data-id="${p.id}">`:''}<button class="picture-open" data-id="${p.id}" aria-label="查看 ${esc(p.title)}"><img alt="${esc(p.title)}" data-image="${p.id}"><span class="source-chip">${p.source==='DEMO'?'演示素材':p.source==='AI'?'AI 创作':p.reviewStatus==='APPROVED'?'素材':statuses[p.reviewStatus]}</span></button><div class="card-body"><h3>${esc(p.title)}</h3><div class="card-meta"><span class="tag">${esc(p.category||'未分类')}</span><span>${p.width} × ${p.height} · ${bytes(p.bytes)}</span></div><div class="card-tags">${p.tags.map(t=>`<span>#${esc(t)}</span>`).join('')}</div></div></article>`).join('')||'<div class="empty-state"><strong>这里还没有匹配的图片</strong>尝试其他检索条件，或上传第一张素材。</div>';
  $('#page-indicator').textContent=`${state.page} / ${Math.max(1,Math.ceil(result.total/24))}`;$('#previous-page').disabled=state.page===1;$('#next-page').disabled=state.page*24>=result.total;
  $$('.picture-open').forEach(b=>b.onclick=()=>action(()=>pictureModal(Number(b.dataset.id))));
  $$('.select-picture').forEach(input=>input.onchange=()=>{const id=Number(input.dataset.id);input.checked?state.selected.add(id):state.selected.delete(id);updateBatch();});
  await Promise.all(result.records.map(async p=> {
    try {const response=await fetch(p.contentUrl,{headers:state.token?{Authorization:`Bearer ${state.token}`}:{}});if(!response.ok) return;const blob=await response.blob();if(generation!==state.load) return;const url=URL.createObjectURL(blob);state.urls.push(url);const image=$(`[data-image="${p.id}"]`);if(image) image.src=url; }catch { /* Metadata remains usable during an object store outage. */ }
  }));
}
function canEdit(p) { if(!state.user) return false;if(state.space?.type==='PUBLIC') return state.user.role==='ADMIN'||p.uploaderId===state.user.id;return ['OWNER','ADMIN','EDITOR'].includes(state.space?.myRole); }
$('#search-form').onsubmit=e=>{e.preventDefault();state.page=1;action(loadGallery);};$('#mine-filter').onchange=()=>{state.page=1;action(loadGallery);};
$('#previous-page').onclick=()=>{state.page--;action(loadGallery);};$('#next-page').onclick=()=>{state.page++;action(loadGallery);};
function updateBatch() { $('#batch-bar').classList.toggle('hidden',!state.selected.size);$('#selected-count').textContent=`已选择 ${state.selected.size} 张`; }
$('#batch-clear').onclick=()=>{state.selected.clear();$$('.select-picture').forEach(i=>i.checked=false);updateBatch();};
$('#batch-edit').onclick=()=>batchModal(false);$('#batch-delete').onclick=()=>batchModal(true);
function batchModal(remove) {
  const ids=[...state.selected],versions=Object.fromEntries(state.pictures.filter(p=>state.selected.has(p.id)).map(p=>[p.id,p.version]));
  modal(remove?'批量删除图片':'批量修改分类与标签',`<form id="batch-form" class="form"><p class="hint">将${remove?'删除':'更新'} ${ids.length} 张图片。任何图片权限或版本不符，整个操作都会取消。</p>${remove?'':'<label>分类<input name="category" maxlength="40"></label><label>标签<input name="tags" placeholder="用英文逗号分隔，最多 10 个"></label>'}<button class="button ${remove?'danger':''}">${remove?'确认删除':'保存更改'}</button></form>`);
  $('#batch-form').onsubmit=e=>{e.preventDefault();action(async()=>{const data=new FormData(e.target);await api('/api/pictures/batch',{method:'POST',body:{ids,versions,remove,category:data.get('category')||'',tags:(data.get('tags')||'').split(',').map(t=>t.trim()).filter(Boolean)}});closeModal();toast('批量操作已完成');await loadGallery();});};
}
function uploadModal() {
  if(!requireUser()) return;modal('把新灵感加入图库',`<form id="upload-form" class="form"><label>目标空间<select name="spaceId">${spaceOptions()}</select></label><label>图片文件<input name="file" type="file" required accept="image/png,image/jpeg"></label><label>图片名称<input name="title" required maxlength="100" placeholder="给素材一个容易检索的名字"></label><div class="form-row"><label>分类<input name="category" maxlength="40" placeholder="如：产品"></label><label>标签<input name="tags" placeholder="设计,灵感"></label></div><small>接受 PNG / JPEG，单张不超过 10 MB、1600 万像素。公开上传后需等待管理员审核。</small><button class="button">上传图片</button></form>`);
  if(state.space) $('[name="spaceId"]').value=state.space.id;
  $('[name="file"]').onchange=e=>{if(!$('[name="title"]').value&&e.target.files[0]) $('[name="title"]').value=e.target.files[0].name.replace(/\.[^.]+$/,'');};
  $('#upload-form').onsubmit=e=> {
    e.preventDefault();const data=new FormData(e.target),file=data.get('file');if(file.size>10*1024*1024) return toast('图片不能超过 10 MB');data.set('tags',JSON.stringify(String(data.get('tags')||'').split(',').map(t=>t.trim()).filter(Boolean)));const button=$('button',e.target);button.disabled=true;
    action(async()=> { try {const p=await api('/api/pictures',{method:'POST',body:data});closeModal();toast(p.reviewStatus==='PENDING'?'上传成功，等待审核':'图片已加入空间');await loadSpaces();const s=[state.publicSpace,...state.spaces.map(convertSpace)].find(s=>s.id===p.spaceId);await navigate(s?.type==='TEAM'?'team':s?.type==='PRIVATE'?'private':'public',p.spaceId); }finally{button.disabled=false;} });
  };
}
async function pictureModal(id) {
  const p=await api(`/api/pictures/${id}`),editable=canEdit(p),source=state.urls[$$('.picture-card').findIndex(c=>Number(c.dataset.id)===id)];
  modal('素材详情与编辑',`<img id="detail-image" class="picture-preview" alt="${esc(p.title)}"><p class="hint">${p.width} × ${p.height} · ${bytes(p.bytes)} · ${statuses[p.reviewStatus]}${p.source==='DEMO'?' · 程序化演示素材':''}</p>${state.space?.type==='TEAM'?'<div id="collab-state" class="collab-hint">正在连接团队协作…</div>':''}<form id="edit-form" class="form"><label>名称<input name="title" value="${esc(p.title)}" required maxlength="100" ${editable?'':'readonly'}></label><div class="form-row"><label>分类<input name="category" value="${esc(p.category)}" maxlength="40" ${editable?'':'readonly'}></label><label>标签<input name="tags" value="${esc(p.tags.join(','))}" ${editable?'':'readonly'}></label></div>${editable?'<button class="button">保存名称与标签</button>':''}</form>${editable?`<details><summary class="subheading">裁剪与旋转图片</summary><form id="transform-form" class="form"><div class="form-row"><label>X<input name="x" type="number" min="0" value="0" required></label><label>Y<input name="y" type="number" min="0" value="0" required></label><label>宽<input name="width" type="number" min="1" value="${p.width}" required></label><label>高<input name="height" type="number" min="1" value="${p.height}" required></label></div><label>顺时针旋转<select name="rotation"><option value="0">不旋转</option><option value="90">90°</option><option value="180">180°</option><option value="270">270°</option></select></label><button class="button secondary">应用图片编辑</button></form></details>`:''}<div class="space-actions subheading"><button id="download-image" class="button secondary small">下载图片</button>${editable?'<button id="delete-picture" class="text-button danger">删除图片</button>':''}${state.tab==='admin'?'<button id="approve-picture" class="button small">通过审核</button><button id="reject-picture" class="button secondary small">拒绝</button>':''}</div><p id="edit-error" class="error hidden"></p>`);
  state.edit=p;state.dirty=false;
  const response=await fetch(p.contentUrl,{headers:state.token?{Authorization:`Bearer ${state.token}`}:{}});if(response.ok){const url=URL.createObjectURL(await response.blob());state.urls.push(url);if($('#detail-image')) $('#detail-image').src=url;}
  $('#edit-form').addEventListener('input',()=>state.dirty=true);
  $('#edit-form').onsubmit=e=>{e.preventDefault();if(!editable) return;const f=new FormData(e.target);saveEdit(()=>api(`/api/pictures/${id}`,{method:'PUT',body:{title:f.get('title'),category:f.get('category'),tags:String(f.get('tags')).split(',').map(t=>t.trim()).filter(Boolean),version:state.edit.version}}));};
  if(editable) {
    $('#transform-form').onsubmit=e=>{e.preventDefault();const data=Object.fromEntries([...new FormData(e.target)].map(([k,v])=>[k,Number(v)]));saveEdit(()=>api(`/api/pictures/${id}/transform`,{method:'POST',body:{...data,version:state.edit.version}}));};
    $('#delete-picture').onclick=()=> { modal('确认删除素材','<p class="hint">此操作将删除图片记录并释放空间配额。</p><button id="confirm-delete" class="button danger">确认删除</button>');$('#confirm-delete').onclick=()=>action(async()=>{await api(`/api/pictures/${id}?version=${p.version}`,{method:'DELETE'});closeModal();toast('图片已删除');await loadGallery();}); };
  }
  $('#download-image').onclick=()=>action(async()=>{const r=await fetch(p.contentUrl,{headers:state.token?{Authorization:`Bearer ${state.token}`}:{}});if(!r.ok) throw new Error('没有图片读取权限');const url=URL.createObjectURL(await r.blob());const a=document.createElement('a');a.href=url;a.download=`picture-${id}.png`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
  if(state.tab==='admin') { $('#approve-picture').onclick=()=>review('APPROVED',p);$('#reject-picture').onclick=()=>review('REJECTED',p); }
  if(state.space?.type==='TEAM') await connectCollaboration(p);
}
async function saveEdit(work) {
  try { await work();closeModal();toast('图片已更新');await loadGallery(); }
  catch(error) { if($('#edit-error')) {$('#edit-error').textContent=error.code==='CONFLICT'?'其他成员已修改此图片，请关闭并重新打开后再保存。':error.message;$('#edit-error').classList.remove('hidden');}else toast(error.message); }
}
async function connectCollaboration(p) {
  const data=await api('/api/collaboration/ticket',{method:'POST',body:{pictureId:p.id}});
  const socket=new WebSocket(`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}${data.path}?ticket=${encodeURIComponent(data.ticket)}`);state.socket=socket;
  socket.onopen=()=>{if($('#collab-state')) $('#collab-state').textContent='● 团队协作已连接 · 修改会同步到其他成员';};
  socket.onmessage=async e=> {
    let data;try{data=JSON.parse(e.data);}catch{return;}if(!state.edit||data.picture.id!==state.edit.id) return;
    if(data.picture.version!==state.edit.version) {
      if(state.dirty) { if($('#collab-state')) $('#collab-state').textContent='其他成员已修改此图片。当前草稿已保留，请重新打开图片获取最新版本。'; }
      else {
        state.edit=data.picture;if($('[name="title"]')) {$('[name="title"]').value=data.picture.title;$('[name="category"]').value=data.picture.category;$('[name="tags"]').value=data.picture.tags.join(',');}
        if($('#transform-form')) {$('#transform-form [name="width"]').value=data.picture.width;$('#transform-form [name="height"]').value=data.picture.height;}
        if($('#collab-state')) $('#collab-state').textContent='● 已同步团队的最新修改';
        try {const response=await fetch(data.picture.contentUrl,{headers:{Authorization:`Bearer ${state.token}`}});if(response.ok&&state.edit?.id===data.picture.id){const url=URL.createObjectURL(await response.blob());state.urls.push(url);if($('#detail-image')) $('#detail-image').src=url;}}catch{ /* The next snapshot can repair a temporary image read failure. */ }
      }
    }
  };
  socket.onclose=()=>{if($('#collab-state')) $('#collab-state').textContent='协作连接已断开；保存仍会校验版本，请刷新获取最新内容。';};
}
function review(status,p) {
  modal(status==='APPROVED'?'通过公开审核':'拒绝公开审核',`<form id="review-form" class="form"><label>审核说明<textarea name="reason" maxlength="200" placeholder="${status==='APPROVED'?'可填写审核备注':'请填写拒绝原因'}" ${status==='REJECTED'?'required':''}></textarea></label><button class="button">确认${status==='APPROVED'?'通过':'拒绝'}</button></form>`);
  $('#review-form').onsubmit=e=>{e.preventDefault();action(async()=>{await api(`/api/pictures/${p.id}/review`,{method:'POST',body:{version:p.version,status,reason:new FormData(e.target).get('reason')}});closeModal();toast('审核结果已保存');await loadGallery();});};
}
function renderAi() {
  $('#gallery-section').classList.add('hidden');$('#extra-section').classList.remove('hidden');const demo=state.mode.aiMode==='demo';
  $('#extra-section').innerHTML=`<div class="ai-layout"><div class="panel"><h2>描绘你的下一张作品</h2>${demo?'<p class="collab-hint">当前为演示模式：生成带标记的程序化图形，用于体验任务流程，不会调用 AI 模型。</p>':'<p class="hint">使用阿里云百炼绘图，提交后后台持续查询生成进度。生成结果按目标图库权限保存。</p>'}<form id="ai-form" class="form"><label>目标空间<select name="spaceId">${spaceOptions()}</select></label><label>作品名称<input name="title" required maxlength="100" placeholder="为这次创作起个名字"></label><label>创作描述<textarea name="prompt" required maxlength="1500" placeholder="例如：柔和晨光中的极简建筑，米白色墙面与绿色庭院，细腻光影，横向构图"></textarea></label><small>一次生成 1 张图片。同一任务无需重复提交，可在右侧查看进度。</small><button class="button">✧ 提交创作任务</button></form></div><div class="panel"><h2>我的创作任务</h2><div id="jobs"><p class="hint">正在读取任务…</p></div></div></div>`;
  $('#ai-form').onsubmit=e=>{e.preventDefault();const f=new FormData(e.target);const button=$('button',e.target);button.disabled=true;action(async()=>{try{await api('/api/ai/jobs',{method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:{spaceId:Number(f.get('spaceId')),title:f.get('title'),prompt:f.get('prompt')}});toast('创作任务已提交');await refreshJobs();}finally{button.disabled=false;}});};
  action(refreshJobs);jobTimer=setInterval(()=>action(refreshJobs),4000);
}
async function refreshJobs() {
  if(state.tab!=='ai'||!$('#jobs')) return;const list=await api('/api/ai/jobs');
  $('#jobs').innerHTML=list.map(j=>`<article class="job"><h3>${esc(j.title)}</h3><span class="status ${['FAILED','UNKNOWN'].includes(j.status)?'failed':''}">${statuses[j.status]}</span><small>${j.provider==='demo'?'演示任务':'百炼绘图'} · ${esc(j.createdAt.replace('T',' ').slice(0,16))}${j.errorCode?` · ${esc(j.errorCode)}`:''}</small>${j.pictureId?`<button class="link-button open-result" data-space="${j.spaceId}" data-id="${j.pictureId}">查看归档图片 ↗</button>`:''}${j.status==='UNKNOWN'?'<p class="hint">提交结果不确定，系统不会自动重新提交。请联系管理员核查云任务。</p>':''}</article>`).join('')||'<div class="empty-state">你的第一份创作，正在等一个好想法。</div>';
  $$('.open-result').forEach(b=>b.onclick=()=>action(async()=>{const data=await api(`/api/spaces/${b.dataset.space}`);await navigate(data.space.type==='TEAM'?'team':data.space.type==='PRIVATE'?'private':'public',data.space.id);await pictureModal(Number(b.dataset.id));}));
}
function renderAnalytics(spaceId) {
  $('#gallery-section').classList.add('hidden');$('#extra-section').classList.remove('hidden');const options=state.spaces.map(convertSpace);if(state.user.role==='ADMIN') options.unshift(state.publicSpace);
  if(!options.length) { $('#extra-section').innerHTML='<div class="empty-state"><strong>创建空间后查看分析</strong>这里会展示真实图片数量、存储用量和上传分布。</div>';return; }
  $('#extra-section').innerHTML=`<div class="panel"><label class="form">分析空间<select id="analysis-space">${options.map(s=>`<option value="${s.id}">${esc(s.name)}</option>`).join('')}</select></label></div><div id="analysis-data"></div>`;
  if(spaceId&&options.some(s=>s.id===spaceId)) $('#analysis-space').value=spaceId;$('#analysis-space').onchange=()=>action(refreshAnalysis);action(refreshAnalysis);
}
async function refreshAnalysis() {
  const id=Number($('#analysis-space').value),data=await api(`/api/spaces/${id}/analytics`),s=data.space;
  const bars=(items,label,max)=>items.map(i=>`<div class="bar-row"><span>${esc(i[label]||'未分类')}</span><meter min="0" max="${max}" value="${i.count}"></meter><span>${i.count} 张</span></div>`).join('')||'<p class="hint">暂无数据</p>';
  $('#analysis-data').innerHTML=`<div class="metric-grid"><div class="metric-card"><small>图片数量</small><strong>${s.usedCount}</strong><small>最多 ${s.maxCount} 张</small></div><div class="metric-card"><small>已用存储</small><strong>${bytes(s.usedBytes)}</strong><small>总容量 ${bytes(s.maxBytes)}</small></div><div class="metric-card"><small>容量使用率</small><strong>${(s.usedBytes/s.maxBytes*100).toFixed(1)}%</strong><meter min="0" max="${s.maxBytes}" value="${s.usedBytes}"></meter></div></div><div class="analysis-grid"><div class="panel"><h2>内容分类</h2>${bars(data.categories,'category',Math.max(1,...data.categories.map(i=>i.count)))}</div><div class="panel"><h2>成员贡献</h2>${bars(data.contributors,'display_name',Math.max(1,...data.contributors.map(i=>i.count)))}</div><div class="panel"><h2>近 30 天上传趋势</h2>${bars(data.trend,'date',Math.max(1,...data.trend.map(i=>i.count)))}</div>${s.type==='TEAM'?'<div class="panel"><h2>最近团队操作</h2><div id="audit-list"><p class="hint">读取中</p></div></div>':''}</div>`;
  if(s.type==='TEAM') { try { const rows=await api(`/api/spaces/${id}/audit`);$('#audit-list').innerHTML=rows.map(r=>`<div class="audit-row">${esc(r.display_name)} · ${esc(r.action)}<br>${esc(r.created_at)} · ${esc(r.target_id)}</div>`).join('')||'<p class="hint">暂无操作记录</p>'; }catch{$('#audit-list').innerHTML='<p class="hint">仅团队管理员可查看操作审计。</p>';} }
}
async function init() {
  try {
    const data=await api('/api/spaces/public');state.publicSpace={...data.space,myRole:'GUEST'};state.mode=data;$('#mode-label').textContent=`${data.storageMode==='cos'?'云端存储':'本地存储'} · ${data.aiMode==='demo'?'绘图演示模式':'百炼 AI 绘图'}`;
    if(state.token) { try{state.user=await api('/api/auth/me');await loadSpaces();}catch{state.token=null;sessionStorage.removeItem('framehub-token');} }
    updateUser();await navigate('public');
  } catch(error) { $('#gallery').innerHTML=`<div class="empty-state"><strong>暂时无法连接图库</strong>${esc(error.message)}<br>请确认服务已启动后刷新页面。</div>`;$('#mode-label').textContent='图库连接失败'; }
}
init();
