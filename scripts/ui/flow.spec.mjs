import {test,expect} from '@playwright/test';
import {mkdir} from 'node:fs/promises';
test('Knife4j loads the generated OpenAPI document',async({page,request},testInfo)=>{
  const spec=await request.get('/v3/api-docs');expect(spec.ok()).toBe(true);const body=await spec.json();expect(body.openapi).toMatch(/^3\./);expect(body.paths['/api/pictures']).toBeTruthy();
  await page.goto('/doc.html');await expect(page.locator('body')).toContainText('FrameHub',{timeout:20000});
  await mkdir('test-results/screenshots',{recursive:true});await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-api-docs.png`,fullPage:true});
});
test('public gallery, registration, private upload, editing, analysis and demo AI task',async({page,request},testInfo)=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('/');await expect(page.locator('.picture-card').first()).toBeVisible();await expect(page.locator('.picture-card').first().locator('img')).toHaveJSProperty('complete',true);await mkdir('test-results/screenshots',{recursive:true});await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-public.png`,fullPage:true});
  await page.getByRole('button',{name:'登录 / 注册'}).click();await page.getByRole('button',{name:'注册',exact:true}).click();
  const username='ui_'+testInfo.project.name+'_'+Date.now().toString().slice(-8);await page.getByLabel('显示名称').fill('创作测试');await page.getByLabel('用户名',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill('BrowserTest2026!');await page.getByRole('button',{name:'注册并登录'}).click();await expect(page.locator('#modal')).not.toBeVisible();
  await page.getByRole('button',{name:'私有图库',exact:false}).click();await page.getByRole('button',{name:'创建私有空间',exact:true}).click();await page.getByLabel('空间名称',{exact:true}).fill('我的创作素材');await page.getByRole('button',{name:'创建空间',exact:true}).click();await expect(page.locator('#modal')).not.toBeVisible();
  const publicInfo=(await (await request.get('/api/spaces/public')).json()).data;const sample=(await (await request.get(`/api/pictures?spaceId=${publicInfo.space.id}`)).json()).data.records[0];const content=await request.get(sample.contentUrl);
  await page.getByRole('button',{name:'＋ 上传图片',exact:true}).click();await page.getByLabel('图片文件').setInputFiles({name:'creative.png',mimeType:'image/png',buffer:await content.body()});await page.getByLabel('图片名称').fill('品牌构成测试');await page.locator('#upload-form input[name="category"]').fill('品牌');await page.locator('#upload-form input[name="tags"]').fill('灵感,品牌');await page.locator('#upload-form').getByRole('button',{name:'上传图片',exact:true}).click();await expect(page.locator('#modal')).not.toBeVisible();await expect(page.locator('.picture-card')).toHaveCount(1);
  await page.locator('.picture-open').click();await page.locator('#edit-form input[name="title"]').fill('更新后的品牌构成');await page.getByRole('button',{name:'保存名称与标签'}).click();await expect(page.locator('#modal')).not.toBeVisible();await expect(page.locator('.card-body h3')).toHaveText('更新后的品牌构成');
  await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-private.png`,fullPage:true});
  await page.getByRole('button',{name:'空间分析',exact:false}).click();await expect(page.locator('#analysis-data .metric-card').first().locator('strong')).toHaveText('1');await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-analytics.png`,fullPage:true});
  await page.getByRole('button',{name:'AI 创作室',exact:false}).click();await page.locator('#ai-form select[name="spaceId"]').selectOption({label:'我的创作素材 · 私有'});await page.locator('#ai-form input[name="title"]').fill('演示任务作品');await page.locator('#ai-form textarea[name="prompt"]').fill('极简几何花园');await page.getByRole('button',{name:'提交创作任务',exact:false}).click();await expect(page.locator('#jobs .status')).toHaveText('已完成',{timeout:35000});await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-ai.png`,fullPage:true});
  await page.getByRole('button',{name:'创作测试 · 退出'}).click();await expect(page.getByRole('button',{name:'登录 / 注册'})).toBeVisible();await expect(page.locator('.picture-card').first()).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(errors).toEqual([]);
});
async function register(page,username,name) {
  await page.goto('/');await expect(page.locator('.picture-card').first()).toBeVisible();await page.getByRole('button',{name:'登录 / 注册'}).click();await page.getByRole('button',{name:'注册',exact:true}).click();
  await page.getByLabel('显示名称').fill(name);await page.getByLabel('用户名',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill('BrowserTest2026!');await page.getByRole('button',{name:'注册并登录'}).click();await expect(page.locator('#modal')).not.toBeVisible();
}
async function upload(page,request,title) {
  const publicInfo=(await (await request.get('/api/spaces/public')).json()).data;const sample=(await (await request.get(`/api/pictures?spaceId=${publicInfo.space.id}`)).json()).data.records[0];const content=await request.get(sample.contentUrl);
  await page.getByRole('button',{name:'＋ 上传图片',exact:true}).click();await page.getByLabel('图片文件').setInputFiles({name:'creative.png',mimeType:'image/png',buffer:await content.body()});await page.getByLabel('图片名称').fill(title);await page.locator('#upload-form input[name="category"]').fill('品牌');await page.locator('#upload-form').getByRole('button',{name:'上传图片',exact:true}).click();await expect(page.locator('#modal')).not.toBeVisible();
}
test('team invitation and two-member live collaborative editing',async({page,browser,request},testInfo)=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));const stamp=Date.now().toString().slice(-8);await register(page,`team_${testInfo.project.name}_${stamp}`,'团队所有者');
  await page.getByRole('button',{name:'团队空间',exact:false}).click();await page.getByRole('button',{name:'＋ 创建团队空间'}).click();await page.getByLabel('空间名称',{exact:true}).fill('品牌协作工作室');await page.getByRole('button',{name:'创建空间',exact:true}).click();await expect(page.locator('#modal')).not.toBeVisible();
  await upload(page,request,'团队初始素材');await expect(page.locator('.picture-card')).toHaveCount(1);
  await page.getByRole('button',{name:'成员与邀请'}).click();await page.getByRole('button',{name:'生成邀请代码'}).click();await expect(page.locator('#invite-output')).toContainText('邀请代码');const token=(await page.locator('#invite-output').innerText()).match(/[A-Za-z0-9_-]{43}/)[0];await page.getByRole('button',{name:'关闭',exact:true}).click();
  await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-team.png`,fullPage:true});
  const context=await browser.newContext({baseURL:'http://localhost:8080',viewport:testInfo.project.use.viewport,isMobile:testInfo.project.use.isMobile,hasTouch:testInfo.project.use.hasTouch});
  try {
    const editor=await context.newPage();editor.on('pageerror',e=>errors.push(e.message));await register(editor,`edit_${testInfo.project.name}_${stamp}`,'团队编辑者');await editor.getByRole('button',{name:'加入团队',exact:true}).click();await editor.getByLabel('邀请代码',{exact:true}).fill(token);await editor.getByRole('button',{name:'接受邀请'}).click();await expect(editor.locator('#modal')).not.toBeVisible();await expect(editor.locator('.picture-card')).toHaveCount(1);
    await editor.locator('.picture-open').click();await expect(editor.locator('#collab-state')).toContainText('团队协作已连接');await page.locator('.picture-open').click();await expect(page.locator('#collab-state')).toContainText('团队协作已连接');
    await page.locator('#edit-form input[name="title"]').fill('来自另一位成员的修改');await page.getByRole('button',{name:'保存名称与标签'}).click();await expect(page.locator('#modal')).not.toBeVisible();await expect(editor.locator('#edit-form input[name="title"]')).toHaveValue('来自另一位成员的修改',{timeout:17000});
    await editor.screenshot({path:`test-results/screenshots/${testInfo.project.name}-collaboration.png`,fullPage:true});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(errors).toEqual([]);
  } finally {await context.close();}
});
test('administrator reviews public image and suspends a user through UI',async({page,request},testInfo)=>{
  const stamp=Date.now().toString().slice(-8),username=`review_${testInfo.project.name}_${stamp}`;await register(page,username,'公共上传者');await upload(page,request,'等待管理员审核的图片');await page.getByRole('button',{name:'公共上传者 · 退出'}).click();await expect(page.getByRole('button',{name:'登录 / 注册'})).toBeVisible();
  await page.getByRole('button',{name:'登录 / 注册'}).click();await page.getByLabel('用户名',{exact:true}).fill('admin');await page.getByLabel('密码',{exact:true}).fill(process.env.BOOTSTRAP_ADMIN_PASSWORD||'DemoAdmin2026!');await page.getByRole('button',{name:'登录',exact:true}).last().click();await expect(page.locator('#modal')).not.toBeVisible();
  await page.getByRole('button',{name:'审核管理',exact:false}).click();await page.locator('.picture-card').filter({hasText:'等待管理员审核的图片'}).first().locator('.picture-open').click();await page.getByRole('button',{name:'通过审核'}).click();await page.getByLabel('审核说明').fill('浏览器审核验证');await page.getByRole('button',{name:'确认通过'}).click();await expect(page.locator('#modal')).not.toBeVisible();
  await page.locator('#user-search input[name="q"]').fill(username);await page.locator('#user-search').getByRole('button',{name:'搜索账号'}).click();await expect(page.locator('.account-toggle')).toHaveCount(1);await page.getByRole('button',{name:'停用账号',exact:true}).click();await expect(page.getByRole('button',{name:'启用账号',exact:true})).toBeVisible();
  await page.screenshot({path:`test-results/screenshots/${testInfo.project.name}-admin.png`,fullPage:true});
  const login=await request.post('/api/auth/login',{data:{username,password:'BrowserTest2026!'}});expect(login.status()).toBe(401);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
