const STORAGE_KEY = 'littlepages-admin-preview-draft-v1';
const STAFF_STORAGE_KEY = 'littlepages-admin-staff-role-preview-v1';
const HISTORY_STORAGE_KEY = 'littlepages-admin-preview-history-v1';
const MODERATION_STORAGE_KEY = 'littlepages-admin-moderation-preview-v1';
const sampleModerationReports = [
  { id: 'sample-report-1', content: 'The forest guide was so helpful for our family story time.', reason: 'Sample: off-topic content', status: 'open' },
  { id: 'sample-report-2', content: 'A fictional example post used to preview the moderation workflow.', reason: 'Sample: privacy concern', status: 'open' }
];
const fallback = {
  schemaVersion: 1,
  revision: 1,
  catalog: {
    productTypes: [
      { id: 'book', label: { en: 'Books', bn: 'বই' }, renderer: 'book_v1', enabled: true, attributes: [
        { id: 'author', label: { en: 'Author', bn: 'লেখক' }, valueType: 'text', required: true },
        { id: 'age_band', label: { en: 'Recommended age', bn: 'প্রস্তাবিত বয়স' }, valueType: 'text', required: true },
        { id: 'page_count', label: { en: 'Page count', bn: 'পৃষ্ঠা সংখ্যা' }, valueType: 'number', required: false }
      ] },
      { id: 'toy', label: { en: 'Toys', bn: 'খেলনা' }, renderer: 'physical_good_v1', enabled: true, attributes: [
        { id: 'age_band', label: { en: 'Recommended age', bn: 'প্রস্তাবিত বয়স' }, valueType: 'text', required: true },
        { id: 'material', label: { en: 'Material', bn: 'উপাদান' }, valueType: 'enum', required: false, options: [
          { value: 'wood', label: { en: 'Wood', bn: 'কাঠ' } }, { value: 'paper', label: { en: 'Paper', bn: 'কাগজ' } },
          { value: 'fabric', label: { en: 'Fabric', bn: 'কাপড়' } }, { value: 'plastic', label: { en: 'Plastic', bn: 'প্লাস্টিক' } }
        ] }
      ] }
    ],
    storefronts: [
      { id: 'books', slug: 'books', title: { en: 'Books', bn: 'বই' }, productTypeIds: ['book'], categoryIds: ['animals', 'friendship', 'nature', 'bedtime', 'courage'], landingPageId: 'books-home', enabled: true, navigationVisible: true, sortOrder: 10, regions: ['BD'] },
      { id: 'toys', slug: 'toys', title: { en: 'Toys', bn: 'খেলনা' }, productTypeIds: ['toy'], categoryIds: [], landingPageId: null, enabled: false, navigationVisible: false, sortOrder: 20, regions: ['BD'] }
    ],
    productCategories: [
      ['animals', 'Animals', 'প্রাণী', 10], ['friendship', 'Friendship', 'বন্ধুত্ব', 20], ['nature', 'Nature', 'প্রকৃতি', 30], ['bedtime', 'Bedtime', 'ঘুমের আগে', 40], ['courage', 'Courage', 'সাহস', 50]
    ].map(([id, en, bn, sortOrder]) => ({ id, storefrontId: 'books', label: { en, bn }, enabled: true, sortOrder: Number(sortOrder) })),
    storePages: [{ id: 'books-home', title: { en: 'Books', bn: 'বই' }, schemaVersion: 1, enabled: true, blocks: [
      { id: 'welcome', type: 'editorial_text_v1', body: { en: 'Stories for every little reader.', bn: 'প্রতিটি ছোট পাঠকের জন্য গল্প।' } },
      { id: 'featured-books', type: 'product_collection_v1', title: { en: 'Popular this week', bn: 'এই সপ্তাহের জনপ্রিয়' }, query: { storefrontId: 'books' }, layout: 'carousel' }
    ] }]
  }
};

let config;
let products = [];
let staffRoleDraft = [];
let draftHistory = [];
let moderationPreview;
let selectedPageId = '';
let eventsBound = false;
const byId = id => document.getElementById(id);
const clone = value => JSON.parse(JSON.stringify(value));
const newId = prefix => `${prefix}-${Math.random().toString(36).slice(2, 8)}`;
const text = (tag, value, className) => { const node = document.createElement(tag); node.textContent = value ?? ''; if (className) node.className = className; return node; };
const node = text;

async function loadConfig() {
  try {
    const response = await fetch('../firebase/config/provisional-defaults.v1.json');
    if (!response.ok) throw new Error('defaults unavailable');
    config = await response.json();
  } catch {
    config = clone(fallback);
  }
  try {
    const productsResponse = await fetch('../firebase/config/demo-products.v1.json');
    products = productsResponse.ok ? await productsResponse.json() : [];
  } catch { products = []; }
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved) {
    try {
      const draft = JSON.parse(saved);
      if (draft.configuration) { config = draft.configuration; products = draft.products ?? products; }
      else { config = draft; }
    } catch { localStorage.removeItem(STORAGE_KEY); }
  }
  config.catalog.productTypes ??= clone(fallback.catalog.productTypes);
  config.catalog.productTypes = config.catalog.productTypes.map(type => {
    const defaults = fallback.catalog.productTypes.find(item => item.id === type.id);
    return { ...type, attributes: type.attributes?.length ? type.attributes : clone(defaults?.attributes ?? []) };
  });
  config.catalog.storefronts ??= clone(fallback.catalog.storefronts);
  config.catalog.productCategories ??= clone(fallback.catalog.productCategories);
  config.catalog.storePages ??= clone(fallback.catalog.storePages);
  selectedPageId = config.catalog.storePages[0]?.id ?? '';
  try { staffRoleDraft = JSON.parse(localStorage.getItem(STAFF_STORAGE_KEY) || '[]'); } catch { staffRoleDraft = []; }
  try { draftHistory = JSON.parse(localStorage.getItem(HISTORY_STORAGE_KEY) || '[]'); } catch { draftHistory = []; }
  try { moderationPreview = JSON.parse(localStorage.getItem(MODERATION_STORAGE_KEY) || 'null'); } catch { moderationPreview = null; }
  moderationPreview ??= { reports: clone(sampleModerationReports), events: [] };
  bindEvents();
  render();
}

function bindEvents() {
  if (eventsBound) return;
  eventsBound = true;
  byId('addStoreButton').addEventListener('click', () => { byId('storeForm').hidden = false; byId('storeForm').querySelector('input').focus(); });
  byId('cancelStoreButton').addEventListener('click', () => { byId('storeForm').reset(); byId('storeForm').hidden = true; });
  byId('storeForm').addEventListener('submit', event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const titleEn = data.get('titleEn').trim();
    const titleBn = data.get('titleBn').trim();
    const typeId = data.get('typeId');
    const id = slug(titleEn) || newId('store');
    if (!titleEn || !titleBn || config.catalog.storefronts.some(store => store.id === id)) return;
    config.catalog.storefronts.push({ id, slug: id, title: { en: titleEn, bn: titleBn }, productTypeIds: [typeId], categoryIds: [], landingPageId: null, navigationVisible: true, enabled: true, sortOrder: (config.catalog.storefronts.length + 1) * 10, regions: ['BD'] });
    event.currentTarget.reset(); event.currentTarget.hidden = true; render();
  });
  byId('addProductButton').addEventListener('click', () => openProductForm());
  byId('addProductTypeButton').addEventListener('click', () => { byId('productTypeForm').hidden = false; byId('productTypeForm').elements.labelEn.focus(); });
  byId('cancelProductTypeButton').addEventListener('click', () => { byId('productTypeForm').reset(); byId('productTypeForm').hidden = true; });
  byId('productTypeForm').addEventListener('submit', event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const labelEn = String(data.get('labelEn') || '').trim(); const labelBn = String(data.get('labelBn') || '').trim();
    const renderer = String(data.get('renderer') || 'book_v1');
    const id = slug(labelEn) || newId('type');
    const template = config.catalog.productTypes.find(type => type.renderer === renderer);
    if (!labelEn || !labelBn || !template || config.catalog.productTypes.some(type => type.id === id)) return;
    config.catalog.productTypes.push({ id, label: { en: labelEn, bn: labelBn }, renderer, schemaVersion: 1, enabled: true, attributes: clone(template.attributes ?? []) });
    event.currentTarget.reset(); event.currentTarget.hidden = true; render();
  });
  byId('cancelProductButton').addEventListener('click', () => { byId('productForm').reset(); byId('productForm').hidden = true; });
  byId('productForm').addEventListener('submit', saveProduct);
  byId('productForm').elements.typeId.addEventListener('change', () => renderAttributeFields());
  byId('staffRoleForm').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget; const email = String(new FormData(form).get('email') || '').trim().toLowerCase();
    const role = String(new FormData(form).get('role') || 'catalog_editor');
    if (!email || staffRoleDraft.some(member => member.email === email)) return;
    staffRoleDraft.push({ id: newId('staff-preview'), email, role });
    localStorage.setItem(STAFF_STORAGE_KEY, JSON.stringify(staffRoleDraft)); form.reset(); renderStaffRoles();
  });
  byId('categoryForm').addEventListener('submit', event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const storeId = data.get('storeId');
    const labelEn = data.get('labelEn').trim();
    const labelBn = data.get('labelBn').trim();
    const id = slug(`${storeId}-${labelEn}`) || newId('category');
    if (!storeId || !labelEn || !labelBn || config.catalog.productCategories.some(category => category.id === id)) return;
    const store = config.catalog.storefronts.find(item => item.id === storeId);
    config.catalog.productCategories.push({ id, storefrontId: storeId, label: { en: labelEn, bn: labelBn }, enabled: true, sortOrder: config.catalog.productCategories.length * 10 + 10 });
    store.categoryIds.push(id);
    event.currentTarget.reset(); render();
  });
  byId('pageSelect').addEventListener('change', event => { selectedPageId = event.target.value; renderPages(); });
  byId('newPageButton').addEventListener('click', () => {
    const number = config.catalog.storePages.length + 1;
    const page = { id: newId('page'), title: { en: `New page ${number}`, bn: `নতুন পৃষ্ঠা ${number}` }, schemaVersion: 1, enabled: true, blocks: [] };
    config.catalog.storePages.push(page); selectedPageId = page.id; render(); byId('pageTitleInput').focus(); byId('pageTitleInput').select();
  });
  byId('pageTitleInput').addEventListener('input', event => { const page = selectedPage(); if (page) page.title.en = event.target.value; });
  byId('pageTitleBnInput').addEventListener('input', event => { const page = selectedPage(); if (page) page.title.bn = event.target.value; });
  byId('pageEnabledInput').addEventListener('change', event => { const page = selectedPage(); if (page) { page.enabled = event.target.checked; renderPages(); renderStores(); } });
  byId('addBlockButton').addEventListener('click', addBlock);
  byId('publishButton').addEventListener('click', () => { renderValidation(); byId('publishDialog').showModal(); });
  byId('closeDialogButton').addEventListener('click', () => byId('publishDialog').close());
  byId('saveDraftButton').addEventListener('click', () => { saveLocalDraft(); byId('publishDialog').close(); render(); });
  byId('exportDraftButton').addEventListener('click', exportDraft);
  byId('resetButton').addEventListener('click', async () => { localStorage.removeItem(STORAGE_KEY); localStorage.removeItem(STAFF_STORAGE_KEY); localStorage.removeItem(HISTORY_STORAGE_KEY); localStorage.removeItem(MODERATION_STORAGE_KEY); staffRoleDraft = []; draftHistory = []; moderationPreview = null; await loadConfig(); });
}

function render() {
  renderSummary(); renderStores(); renderCategories(); renderPages(); renderProductTypes(); renderPolicies(); renderProducts(); renderStaffRoles(); renderDraftHistory(); renderModerationPreview();
  byId('revisionValue').textContent = `Draft ${config.revision ?? 1}`;
}

function renderModerationPreview() {
  const queue = byId('moderationQueue'); queue.replaceChildren();
  const openReports = moderationPreview.reports.filter(report => report.status === 'open');
  if (!openReports.length) queue.append(text('div', 'No open sample reports. The report history remains below.', 'empty-state'));
  openReports.forEach(report => {
    const card = document.createElement('article'); card.className = 'moderation-card';
    card.append(text('span', report.reason, 'eyebrow'), text('p', report.content));
    const actions = document.createElement('div'); actions.className = 'moderation-actions';
    [['keep', 'Keep sample post'], ['hide', 'Hide sample post']].forEach(([decision, label]) => {
      const button = text('button', label, decision === 'hide' ? 'button button-soft' : 'button button-primary'); button.type = 'button';
      button.addEventListener('click', () => {
        report.status = decision === 'hide' ? 'hidden' : 'kept';
        moderationPreview.events.unshift({ reportId: report.id, action: report.status, actor: 'Local preview moderator', createdAt: new Date().toISOString() });
        moderationPreview.events = moderationPreview.events.slice(0, 30);
        localStorage.setItem(MODERATION_STORAGE_KEY, JSON.stringify(moderationPreview)); renderModerationPreview();
      }); actions.append(button);
    }); card.append(actions); queue.append(card);
  });
  const audit = byId('moderationAudit'); audit.replaceChildren();
  if (!moderationPreview.events.length) audit.append(text('div', 'No preview decisions yet.', 'empty-state'));
  moderationPreview.events.forEach(event => {
    const row = document.createElement('div'); row.className = 'moderation-audit-row';
    row.append(text('strong', `${event.action === 'hidden' ? 'Sample post hidden' : 'Sample post kept'}`));
    row.append(text('span', `${event.actor} · ${new Date(event.createdAt).toLocaleString()}`)); audit.append(row);
  });
}

function saveLocalDraft() {
  config.revision = Number(config.revision ?? 0) + 1;
  const savedDraft = { configuration: clone(config), products: clone(products) };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(savedDraft));
  draftHistory.unshift({ id: newId('draft-revision'), savedAt: new Date().toISOString(), revision: config.revision, draft: savedDraft });
  draftHistory = draftHistory.slice(0, 20);
  localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(draftHistory));
}

function renderDraftHistory() {
  const list = byId('draftHistoryList'); list.replaceChildren();
  if (!draftHistory.length) { list.append(text('div', 'No saved browser drafts yet. Save a draft from Review publish to create a revision.', 'empty-state')); return; }
  draftHistory.forEach(entry => {
    const row = document.createElement('div'); row.className = 'draft-history-row';
    const description = text('div', `Draft ${entry.revision}`, 'store-name');
    description.append(text('small', new Date(entry.savedAt).toLocaleString())); row.append(description);
    const counts = text('span', `${entry.draft.configuration.catalog.storefronts.length} storefronts · ${entry.draft.configuration.catalog.productCategories.length} categories · ${entry.draft.products.length} products`, 'draft-history-counts'); row.append(counts);
    const restore = text('button', 'Restore this draft', 'button button-soft'); restore.type = 'button';
    restore.addEventListener('click', () => {
      if (!window.confirm(`Restore local draft ${entry.revision}? The current preview draft will be saved in history first.`)) return;
      const current = { configuration: clone(config), products: clone(products) };
      draftHistory.unshift({ id: newId('draft-revision'), savedAt: new Date().toISOString(), revision: config.revision, draft: current });
      draftHistory = draftHistory.slice(0, 20);
      config = clone(entry.draft.configuration); products = clone(entry.draft.products);
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ configuration: config, products }));
      localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(draftHistory));
      render();
    }); row.append(restore); list.append(row);
  });
}

function renderSummary() {
  byId('storeCount').textContent = config.catalog.storefronts.filter(store => store.enabled).length;
  byId('categoryCount').textContent = config.catalog.productCategories.filter(category => category.enabled).length;
  byId('pageCount').textContent = config.catalog.storePages.filter(page => page.enabled).length;
}

function renderStores() {
  const list = byId('storeList'); list.replaceChildren();
  const orderedStores = [...config.catalog.storefronts].sort((a, b) => a.sortOrder - b.sortOrder);
  orderedStores.forEach((store, index) => {
    const row = document.createElement('div'); row.className = 'store-row';
    row.append(text('span', store.productTypeIds.includes('book') ? '▤' : '◇', 'store-icon'));
    const name = text('div', store.title.en, 'store-name'); name.append(text('small', `${store.slug} · ${store.productTypeIds.map(typeName).join(', ')}`)); row.append(name);
    row.append(text('span', store.enabled ? 'Visible' : 'Hidden', `status${store.enabled ? '' : ' off'}`));
    const pageSelect = document.createElement('select'); pageSelect.className = 'store-page-select'; pageSelect.setAttribute('aria-label', `Landing page for ${store.title.en}`);
    const noPage = document.createElement('option'); noPage.value = ''; noPage.textContent = 'No page'; pageSelect.append(noPage);
    config.catalog.storePages.forEach(page => { const option = document.createElement('option'); option.value = page.id; option.textContent = page.title.en; pageSelect.append(option); });
    pageSelect.value = store.landingPageId ?? '';
    pageSelect.addEventListener('change', () => { store.landingPageId = pageSelect.value || null; }); row.append(pageSelect);
    const toggle = document.createElement('input'); toggle.type = 'checkbox'; toggle.className = 'switch'; toggle.checked = store.enabled; toggle.setAttribute('aria-label', `${store.enabled ? 'Disable' : 'Enable'} ${store.title.en} storefront`);
    toggle.addEventListener('change', () => { store.enabled = toggle.checked; store.navigationVisible = toggle.checked; render(); }); row.append(toggle);
    const actions = document.createElement('div'); actions.className = 'entity-actions';
    const rename = text('button', 'Rename'); rename.type = 'button'; rename.addEventListener('click', () => {
      const titleEn = window.prompt('Storefront name in English', store.title.en)?.trim();
      if (!titleEn) return;
      const titleBn = window.prompt('Storefront name in Bangla', store.title.bn)?.trim();
      if (!titleBn) return;
      store.title = { ...store.title, en: titleEn, bn: titleBn }; render();
    }); actions.append(rename);
    [['↑', -1, 'Move storefront up'], ['↓', 1, 'Move storefront down']].forEach(([label, delta, accessibleLabel]) => {
      const move = text('button', label); move.type = 'button'; move.title = accessibleLabel; move.setAttribute('aria-label', `${accessibleLabel}: ${store.title.en}`); move.disabled = (index === 0 && Number(delta) < 0) || (index === orderedStores.length - 1 && Number(delta) > 0);
      move.addEventListener('click', () => { moveByOrder(orderedStores, index, Number(delta)); render(); }); actions.append(move);
    }); row.append(actions);
    list.append(row);
  });
}

function renderCategories() {
  const select = byId('categoryStoreSelect'); select.replaceChildren();
  config.catalog.storefronts.forEach(store => { const option = document.createElement('option'); option.value = store.id; option.textContent = store.title.en; select.append(option); });
  const list = byId('categoryList'); list.replaceChildren();
  [...config.catalog.productCategories].sort((a, b) => a.sortOrder - b.sortOrder).forEach(category => {
    const row = document.createElement('div'); row.className = `category-row${category.enabled ? '' : ' archived'}`;
    const labels = text('div', category.label.en); labels.append(text('small', `${category.label.bn} · ${storeTitle(category.storefrontId)}`)); row.append(labels);
    const actions = document.createElement('div'); actions.className = 'entity-actions';
    const rename = text('button', 'Rename'); rename.type = 'button'; rename.addEventListener('click', () => {
      const labelEn = window.prompt('Category name in English', category.label.en)?.trim();
      if (!labelEn) return;
      const labelBn = window.prompt('Category name in Bangla', category.label.bn)?.trim();
      if (!labelBn) return;
      category.label = { ...category.label, en: labelEn, bn: labelBn }; render();
    }); actions.append(rename);
    const siblings = [...config.catalog.productCategories].filter(item => item.storefrontId === category.storefrontId).sort((a, b) => a.sortOrder - b.sortOrder);
    const categoryIndex = siblings.findIndex(item => item.id === category.id);
    [['↑', -1, 'Move category up'], ['↓', 1, 'Move category down']].forEach(([label, delta, accessibleLabel]) => {
      const move = text('button', label); move.type = 'button'; move.title = accessibleLabel; move.setAttribute('aria-label', `${accessibleLabel}: ${category.label.en}`);
      move.disabled = (categoryIndex === 0 && Number(delta) < 0) || (categoryIndex === siblings.length - 1 && Number(delta) > 0);
      move.addEventListener('click', () => { moveByOrder(siblings, categoryIndex, Number(delta)); render(); }); actions.append(move);
    });
    const archive = text('button', category.enabled ? 'Archive' : 'Restore', 'remove-button'); archive.type = 'button'; archive.addEventListener('click', () => { category.enabled = !category.enabled; render(); }); actions.append(archive);
    row.append(actions); list.append(row);
  });
  const submit = byId('categoryForm').querySelector('button[type="submit"]'); submit.disabled = config.catalog.storefronts.length === 0;
}

function moveByOrder(items, index, delta) {
  const targetIndex = index + delta;
  if (index < 0 || targetIndex < 0 || targetIndex >= items.length) return;
  [items[index], items[targetIndex]] = [items[targetIndex], items[index]];
  items.forEach((item, position) => { item.sortOrder = (position + 1) * 10; });
}

function renderPages() {
  const pages = config.catalog.storePages;
  const select = byId('pageSelect'); select.replaceChildren();
  pages.forEach(page => { const option = document.createElement('option'); option.value = page.id; option.textContent = page.title.en; select.append(option); });
  if (!pages.some(page => page.id === selectedPageId)) selectedPageId = pages[0]?.id ?? '';
  select.value = selectedPageId;
  const page = selectedPage(); byId('pageTitleInput').value = page?.title.en ?? ''; byId('pageTitleBnInput').value = page?.title.bn ?? '';
  byId('pageEnabledInput').checked = Boolean(page?.enabled); byId('pageEnabledInput').disabled = !page;
  const blocks = byId('blockList'); blocks.replaceChildren();
  (page?.blocks ?? []).forEach((block, index) => {
    const row = document.createElement('li'); row.className = 'block-card';
    row.append(text('span', String(index + 1), 'block-number'));
    const summary = text('div', blockName(block.type)); summary.append(text('small', blockTitle(block)));
    row.append(summary);
    const controls = document.createElement('div'); controls.className = 'block-controls';
    [['↑', -1, 'Move up'], ['↓', 1, 'Move down'], ['×', 0, 'Remove']].forEach(([label, movement, description]) => {
      const button = text('button', label); button.type = 'button'; button.title = description;
      button.addEventListener('click', () => {
        if (movement === 0) page.blocks.splice(index, 1);
        else { const target = index + Number(movement); if (target < 0 || target >= page.blocks.length) return; [page.blocks[index], page.blocks[target]] = [page.blocks[target], page.blocks[index]]; }
        renderPages();
      }); controls.append(button);
    });
    row.append(controls); blocks.append(row);
  });
  renderBlockEditors(page);
}

function renderBlockEditors(page) {
  const root = byId('blockEditorList'); root.replaceChildren();
  if (!page?.blocks?.length) { root.append(text('div', 'Add a block above to edit its English and Bangla content.', 'empty-state')); return; }
  page.blocks.forEach((block, index) => {
    const card = document.createElement('fieldset'); card.className = 'block-editor-card';
    const legend = document.createElement('legend'); legend.textContent = `${index + 1}. ${blockName(block.type)}`; card.append(legend);
    const localizedKey = block.type === 'editorial_text_v1' ? 'body' : 'title';
    if (block.type === 'editorial_text_v1' || block.type === 'hero_v1' || block.type === 'category_rail_v1' || block.type === 'product_collection_v1') {
      block[localizedKey] ??= { en: '', bn: '' };
      for (const [locale, labelText] of [['en', 'English'], ['bn', 'বাংলা']]) {
        const label = document.createElement('label'); label.textContent = `${labelText} ${block.type === 'editorial_text_v1' ? 'message' : 'title'}`;
        const input = block.type === 'editorial_text_v1' ? document.createElement('textarea') : document.createElement('input');
        input.maxLength = block.type === 'editorial_text_v1' ? 1000 : 120; input.value = block[localizedKey][locale] ?? '';
        input.addEventListener('input', () => { block[localizedKey][locale] = input.value; }); label.append(input); card.append(label);
      }
    }
    if (block.type === 'product_collection_v1') {
      const query = document.createElement('label'); query.textContent = 'Product storefront';
      const select = document.createElement('select'); const empty = document.createElement('option'); empty.value = ''; empty.textContent = 'All storefront products'; select.append(empty);
      config.catalog.storefronts.forEach(store => { const option = document.createElement('option'); option.value = store.id; option.textContent = store.title.en; select.append(option); });
      select.value = block.query?.storefrontId ?? ''; select.addEventListener('change', () => { block.query ??= {}; if (select.value) block.query.storefrontId = select.value; else delete block.query.storefrontId; }); query.append(select); card.append(query);
      const layoutLabel = document.createElement('label'); layoutLabel.textContent = 'Layout'; const layout = document.createElement('select');
      [['grid', 'Grid'], ['carousel', 'Carousel']].forEach(([value, title]) => { const option = document.createElement('option'); option.value = value; option.textContent = title; layout.append(option); });
      layout.value = block.layout === 'carousel' ? 'carousel' : 'grid'; layout.addEventListener('change', () => { block.layout = layout.value; }); layoutLabel.append(layout); card.append(layoutLabel);
    }
    if (block.type === 'category_rail_v1') {
      const categoryLabel = document.createElement('label'); categoryLabel.textContent = 'Categories (leave empty to show all enabled categories)';
      const select = document.createElement('select'); select.multiple = true; select.size = Math.min(5, Math.max(2, config.catalog.productCategories.length));
      config.catalog.productCategories.forEach(category => { const option = document.createElement('option'); option.value = category.id; option.textContent = `${category.label.en} · ${storeTitle(category.storefrontId)}`; option.selected = (block.categoryIds ?? []).includes(category.id); select.append(option); });
      select.addEventListener('change', () => { block.categoryIds = [...select.selectedOptions].map(option => option.value); }); categoryLabel.append(select); card.append(categoryLabel);
    }
    root.append(card);
  });
}

function renderProductTypes() {
  const list = byId('productTypeList'); list.replaceChildren();
  config.catalog.productTypes.forEach((type, index) => {
    const card = document.createElement('div'); card.className = `type-card${type.enabled ? '' : ' archived'}`; card.append(text('span', type.renderer === 'book_v1' ? '▤' : '◇', 'store-icon'));
    const details = text('div', type.label.en, 'store-name'); details.append(text('small', `${type.label.bn} · ${type.renderer} · ${type.enabled ? 'Enabled' : 'Disabled'}`)); card.append(details);
    const actions = document.createElement('div'); actions.className = 'entity-actions';
    const rename = text('button', 'Rename'); rename.type = 'button'; rename.addEventListener('click', () => {
      const labelEn = window.prompt('Product type name in English', type.label.en)?.trim(); if (!labelEn) return;
      const labelBn = window.prompt('Product type name in Bangla', type.label.bn)?.trim(); if (!labelBn) return;
      type.label = { en: labelEn, bn: labelBn }; render();
    }); actions.append(rename);
    [['↑', -1], ['↓', 1]].forEach(([label, delta]) => { const move = text('button', label); move.type = 'button'; move.disabled = index + Number(delta) < 0 || index + Number(delta) >= config.catalog.productTypes.length; move.setAttribute('aria-label', `Move ${type.label.en} ${Number(delta) < 0 ? 'up' : 'down'}`); move.addEventListener('click', () => { const target = index + Number(delta); [config.catalog.productTypes[index], config.catalog.productTypes[target]] = [config.catalog.productTypes[target], config.catalog.productTypes[index]]; render(); }); actions.append(move); });
    const toggle = text('button', type.enabled ? 'Disable' : 'Enable', 'remove-button'); toggle.type = 'button'; toggle.addEventListener('click', () => { type.enabled = !type.enabled; render(); }); actions.append(toggle);
    card.append(actions); list.append(card);
  });
  const storeType = byId('storeForm').elements.typeId;
  const productType = byId('productForm').elements.typeId;
  const previousStoreType = storeType.value;
  const previousProductType = productType.value;
  [storeType, productType].forEach(select => {
    select.replaceChildren();
    config.catalog.productTypes.filter(type => type.enabled).forEach(type => { const option = document.createElement('option'); option.value = type.id; option.textContent = type.label.en; select.append(option); });
  });
  if ([...storeType.options].some(option => option.value === previousStoreType)) storeType.value = previousStoreType;
  if ([...productType.options].some(option => option.value === previousProductType)) productType.value = previousProductType;
  const categorySelect = byId('productForm').elements.categoryId;
  const previousCategory = categorySelect.value;
  categorySelect.replaceChildren();
  const noCategory = document.createElement('option'); noCategory.value = ''; noCategory.textContent = 'No category'; categorySelect.append(noCategory);
  config.catalog.productCategories.filter(category => category.enabled).forEach(category => { const option = document.createElement('option'); option.value = category.id; option.textContent = `${category.label.en} · ${storeTitle(category.storefrontId)}`; categorySelect.append(option); });
  if ([...categorySelect.options].some(option => option.value === previousCategory)) categorySelect.value = previousCategory;
}

function renderProducts() {
  const list = byId('productList'); list.replaceChildren();
  products.forEach(product => {
    const row = document.createElement('div'); row.className = 'product-admin-row';
    row.append(text('span', product.typeId === 'book' ? '▤' : '◇', 'store-icon'));
    const title = text('div', product.title.en, 'store-name'); title.append(text('small', `${typeName(product.typeId)} · ${product.categoryIds.map(categoryId => config.catalog.productCategories.find(category => category.id === categoryId)?.label.en ?? categoryId).join(', ') || 'Uncategorized'}`)); row.append(title);
    row.append(text('span', product.status, `product-state ${product.status}`));
    const actions = document.createElement('div'); actions.className = 'product-row-actions';
    const edit = text('button', 'Edit'); edit.type = 'button'; edit.addEventListener('click', () => openProductForm(product)); actions.append(edit);
    if (product.status !== 'archived') { const archive = text('button', 'Archive'); archive.type = 'button'; archive.addEventListener('click', () => { product.status = 'archived'; render(); }); actions.append(archive); }
    row.append(actions); list.append(row);
  });
  if (!products.length) list.append(text('div', 'No local sample products yet.', 'empty-state'));
}

function renderStaffRoles() {
  const list = byId('staffRoleList'); list.replaceChildren();
  if (!staffRoleDraft.length) { list.append(text('div', 'No local preview role assignments yet. Add a sample row to inspect the role table.', 'empty-state')); return; }
  staffRoleDraft.forEach(member => {
    const row = document.createElement('div'); row.className = 'staff-role-row';
    const email = text('div', member.email, 'store-name'); email.append(text('small', 'Local sample only')); row.append(email);
    const role = document.createElement('select'); role.setAttribute('aria-label', `Preview role for ${member.email}`);
    [['catalog_editor', 'Catalog editor'], ['publisher', 'Publisher'], ['moderator', 'Moderator'], ['administrator', 'Administrator']].forEach(([value, label]) => { const option = document.createElement('option'); option.value = value; option.textContent = label; role.append(option); });
    role.value = member.role; role.addEventListener('change', () => { member.role = role.value; localStorage.setItem(STAFF_STORAGE_KEY, JSON.stringify(staffRoleDraft)); }); row.append(role);
    const remove = text('button', 'Remove preview row', 'remove-button'); remove.type = 'button'; remove.addEventListener('click', () => { staffRoleDraft = staffRoleDraft.filter(item => item.id !== member.id); localStorage.setItem(STAFF_STORAGE_KEY, JSON.stringify(staffRoleDraft)); renderStaffRoles(); }); row.append(remove);
    list.append(row);
  });
}

function openProductForm(product = null) {
  const form = byId('productForm'); renderProductTypes(); form.reset();
  form.elements.productId.value = product?.id ?? '';
  form.elements.titleEn.value = product?.title.en ?? '';
  form.elements.titleBn.value = product?.title.bn ?? '';
  form.elements.descriptionEn.value = product?.description?.en ?? '';
  form.elements.descriptionBn.value = product?.description?.bn ?? '';
  form.elements.typeId.value = product?.typeId ?? config.catalog.productTypes.find(type => type.enabled)?.id ?? '';
  form.elements.categoryId.value = product?.categoryIds?.[0] ?? '';
  form.elements.price.value = ((product?.variants?.[0]?.priceMinor ?? 0) / 100).toFixed(2);
  form.elements.stockState.value = product?.variants?.[0]?.stockState ?? 'not_applicable';
  form.elements.stockQuantity.value = product?.variants?.[0]?.stockQuantity ?? '';
  renderAttributeFields(product?.attributes ?? {});
  form.elements.status.value = product?.status ?? 'draft';
  form.querySelector('button[type="submit"]').textContent = product ? 'Update product' : 'Save product';
  form.hidden = false;
  form.scrollIntoView({ behavior: 'smooth', block: 'center' });
  form.elements.titleEn.focus();
}

function saveProduct(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const productId = String(data.get('productId') || '');
  const typeId = String(data.get('typeId') || '');
  const type = config.catalog.productTypes.find(item => item.id === typeId && item.enabled);
  const titleEn = String(data.get('titleEn') || '').trim();
  const titleBn = String(data.get('titleBn') || '').trim();
  const price = Number(data.get('price'));
  if (!type || !titleEn || !titleBn || !Number.isFinite(price) || price < 0) return;
  const existing = products.find(item => item.id === productId);
  const id = productId || slug(titleEn) || newId('product');
  if (!productId && products.some(item => item.id === id)) return;
  const categoryId = String(data.get('categoryId') || '');
  const category = config.catalog.productCategories.find(item => item.id === categoryId && item.enabled);
  if (categoryId && !category) return;
  const stockState = String(data.get('stockState'));
  const quantityValue = String(data.get('stockQuantity') || '').trim();
  const quantity = quantityValue ? Math.max(0, Number.parseInt(quantityValue, 10)) : null;
  const attributes = {};
  for (const definition of type.attributes ?? []) {
    const raw = data.get(`attribute:${definition.id}`);
    if (definition.valueType === 'boolean') {
      attributes[definition.id] = form.elements.namedItem(`attribute:${definition.id}`).checked;
    } else if (definition.valueType === 'number') {
      if (raw !== null && String(raw).trim() !== '') attributes[definition.id] = Number(raw);
    } else if (definition.valueType === 'text_list') {
      const values = String(raw ?? '').split(',').map(item => item.trim()).filter(Boolean);
      if (values.length) attributes[definition.id] = values;
    } else {
      const value = String(raw ?? '').trim();
      if (value) attributes[definition.id] = value;
    }
  }
  const oldVariant = existing?.variants?.[0];
  const product = {
    ...(existing ?? {}), id, typeId, status: String(data.get('status')),
    title: { en: titleEn, bn: titleBn },
    description: { en: String(data.get('descriptionEn') || '').trim(), bn: String(data.get('descriptionBn') || '').trim() },
    categoryIds: category ? [category.id] : [], imageAssetIds: existing?.imageAssetIds ?? [], attributes,
    variants: [{
      id: oldVariant?.id ?? `${id}-standard`,
      label: oldVariant?.label ?? { en: typeId === 'book' ? 'Read in app' : 'Standard', bn: typeId === 'book' ? 'অ্যাপে পড়ুন' : 'সাধারণ' },
      priceMinor: Math.round(price * 100), currency: 'BDT', stockState,
      stockQuantity: stockState === 'in_stock' ? quantity ?? 0 : stockState === 'not_applicable' ? null : quantity
    }]
  };
  if (existing) products[products.indexOf(existing)] = product; else products.unshift(product);
  form.reset(); form.hidden = true; render();
}

function renderAttributeFields(values = {}) {
  const root = byId('attributeFields'); root.replaceChildren();
  const type = config.catalog.productTypes.find(item => item.id === byId('productForm').elements.typeId.value);
  (type?.attributes ?? []).forEach(definition => {
    const label = document.createElement('label');
    label.append(document.createTextNode(`${definition.label.en} · ${definition.label.bn}`));
    const input = definition.valueType === 'enum' ? document.createElement('select') : document.createElement('input');
    input.name = `attribute:${definition.id}`;
    input.required = Boolean(definition.required);
    if (definition.valueType === 'boolean') input.type = 'checkbox';
    else if (definition.valueType === 'number') { input.type = 'number'; input.min = '0'; input.step = '1'; }
    else if (definition.valueType === 'text_list') { input.type = 'text'; input.placeholder = 'Separate values with commas'; }
    else input.type = 'text';
    if (definition.valueType === 'enum') {
      const empty = document.createElement('option'); empty.value = ''; empty.textContent = 'Choose…'; input.append(empty);
      (definition.options ?? []).forEach(optionDefinition => { const option = document.createElement('option'); option.value = optionDefinition.value; option.textContent = `${optionDefinition.label.en} · ${optionDefinition.label.bn}`; input.append(option); });
    }
    let savedValue = values[definition.id];
    if (savedValue === undefined && definition.id === 'age_band') savedValue = values.ageBand;
    if (savedValue === undefined && definition.id === 'page_count') savedValue = values.pageCount;
    if (definition.valueType === 'boolean') input.checked = Boolean(savedValue);
    else input.value = Array.isArray(savedValue) ? savedValue.join(', ') : savedValue ?? '';
    label.append(input); root.append(label);
  });
}

function renderPolicies() {
  const root = byId('policyGroups'); root.replaceChildren();
  const groups = [
    { key: 'offlinePolicy', title: 'Offline reading', fields: [
      { key: 'enabled', label: 'Allow offline reading', kind: 'boolean' },
      { key: 'maximumBooksPerFamily', label: 'Books per family', kind: 'number', min: 0 },
      { key: 'accessLeaseDays', label: 'Offline access lease (days)', kind: 'number', min: 1 },
      { key: 'onlineRefreshAfterDays', label: 'Refresh after (days online)', kind: 'number', min: 1 },
      { key: 'encryptAppPrivateStorage', label: 'Encrypt private app storage', kind: 'boolean' },
      { key: 'removeWhenEntitlementExpires', label: 'Remove access after expiry', kind: 'boolean' }
    ] },
    { key: 'accessPolicy', title: 'Book and Premium access', fields: [
      { key: 'freeAccess', label: 'Free book rule', kind: 'select', options: [['book_catalogue_flag', 'Use each book setting'], ['none', 'No free access']] },
      { key: 'premiumBooksRequireActiveSubscription', label: 'Premium books need active subscription', kind: 'boolean' },
      { key: 'premiumNarrationRequiresActiveSubscription', label: 'Narration needs active subscription', kind: 'boolean' },
      { key: 'purchasedBookAccessSurvivesSubscriptionExpiry', label: 'Purchased access survives expiry', kind: 'boolean' }
    ] },
    { key: 'privacyPolicy', title: 'Family privacy', fields: [
      { key: 'parentAccountOwnsChildProfiles', label: 'Parent owns child profiles', kind: 'boolean' },
      { key: 'collectOnlyNicknameAndAgeBandForChildProfile', label: 'Collect nickname and age band only', kind: 'boolean' },
      { key: 'personalizedAdvertising', label: 'Allow personalized advertising', kind: 'boolean' },
      { key: 'parentCanRequestAccountAndChildDataDeletion', label: 'Parent can request data deletion', kind: 'boolean' },
      { key: 'deletionRetentionDays', label: 'Retention period (days, blank = undecided)', kind: 'nullableNumber', min: 0 }
    ] },
    { key: 'physicalCommercePolicy', title: 'Physical commerce', fields: [
      { key: 'enabled', label: 'Enable physical orders', kind: 'boolean' },
      { key: 'availableRegions', label: 'Regions (comma separated)', kind: 'regions' },
      { key: 'paymentMethods', label: 'Payment methods', kind: 'methods' },
      { key: 'showInAppPdfAndPhysicalOptionsWhenBothAvailable', label: 'Offer both book formats when available', kind: 'boolean' },
      { key: 'requireConfirmedStockAndDeliveryQuoteBeforeCheckout', label: 'Confirm stock and delivery before checkout', kind: 'boolean' },
      { key: 'cancellationAndRefundRules', label: 'Cancellation/refund summary', kind: 'nullableText' }
    ] },
    { key: 'communityPolicy', title: 'Parent community', fields: [
      { key: 'parentAccountRequired', label: 'Require parent account', kind: 'boolean' },
      { key: 'moderateBeforePublicVisibility', label: 'Review posts before public visibility', kind: 'boolean' },
      { key: 'prohibitChildContactAndLocationDetails', label: 'Block child contact/location details', kind: 'boolean' },
      { key: 'maximumImageBytes', label: 'Image size limit (MB)', kind: 'megabytes' },
      { key: 'maximumImagesPerPost', label: 'Images per post', kind: 'number', min: 0 },
      { key: 'allowUserCreatedGroups', label: 'Allow parent-created groups', kind: 'boolean' },
      { key: 'reportAndBlockEnabled', label: 'Enable report and block', kind: 'boolean' },
      { key: 'moderationResponseTargetHours', label: 'Response target (hours, blank = undecided)', kind: 'nullableNumber', min: 0 }
    ] }
  ];
  groups.forEach(group => {
    const panel = document.createElement('section'); panel.className = 'policy-card';
    panel.append(node('h3', group.title));
    group.fields.forEach(field => {
      const label = node('label', field.label, `policy-field${field.kind === 'boolean' ? ' boolean-field' : ''}`);
      if (field.kind === 'boolean') {
        const input = document.createElement('input'); input.type = 'checkbox'; input.checked = Boolean(config[group.key][field.key]);
        input.addEventListener('change', () => { config[group.key][field.key] = input.checked; }); label.append(input);
      } else if (field.kind === 'select') {
        const input = document.createElement('select'); field.options.forEach(([value, title]) => { const option = document.createElement('option'); option.value = value; option.textContent = title; input.append(option); });
        input.value = config[group.key][field.key]; input.addEventListener('change', () => { config[group.key][field.key] = input.value; }); label.append(input);
      } else if (field.kind === 'methods') {
        const wrap = document.createElement('span'); wrap.className = 'method-options';
        [['bkash', 'bKash'], ['visa', 'Visa']].forEach(([value, title]) => {
          const optionLabel = node('label', title, 'method-option'); const input = document.createElement('input'); input.type = 'checkbox'; input.checked = config[group.key][field.key].includes(value);
          input.addEventListener('change', () => { config[group.key][field.key] = [...new Set([...config[group.key][field.key].filter(item => item !== value), ...(input.checked ? [value] : [])])]; }); optionLabel.prepend(input); wrap.append(optionLabel);
        }); label.append(wrap);
      } else {
        const input = field.kind === 'nullableText' ? document.createElement('textarea') : document.createElement('input');
        input.type = 'text';
        if (field.kind === 'number' || field.kind === 'nullableNumber' || field.kind === 'megabytes') { input.type = 'number'; input.min = field.min ?? 0; input.step = field.kind === 'megabytes' ? '0.5' : '1'; }
        if (field.kind === 'regions') input.value = config[group.key][field.key].join(', ');
        else if (field.kind === 'megabytes') input.value = (config[group.key][field.key] / (1024 * 1024)).toString();
        else input.value = config[group.key][field.key] ?? '';
        input.addEventListener('input', () => {
          const value = input.value.trim();
          if (field.kind === 'regions') config[group.key][field.key] = value.split(',').map(region => region.trim().toUpperCase()).filter(Boolean);
          else if (field.kind === 'megabytes') config[group.key][field.key] = Math.max(0, Math.round(Number(value || 0) * 1024 * 1024));
          else if (field.kind === 'nullableNumber') config[group.key][field.key] = value ? Math.max(Number(field.min ?? 0), Number(value)) : null;
          else if (field.kind === 'nullableText') config[group.key][field.key] = value || null;
          else config[group.key][field.key] = Math.max(Number(field.min ?? 0), Number(value || 0));
        }); label.append(input);
      }
      panel.append(label);
    });
    root.append(panel);
  });
}

function validateDraft() {
  const errors = [];
  const unique = (items, label) => {
    const ids = items.map(item => item.id);
    if (new Set(ids).size !== ids.length) errors.push(`${label} IDs must be unique.`);
  };
  const validId = id => /^[a-z0-9_-]{2,60}$/.test(id ?? '');
  const localized = (value, label) => {
    if (!value?.en?.trim() || !value?.bn?.trim()) errors.push(`${label} needs English and Bangla labels.`);
  };
  const types = config.catalog.productTypes;
  const stores = config.catalog.storefronts;
  const categories = config.catalog.productCategories;
  const pages = config.catalog.storePages;
  const productsById = new Map(products.map(product => [product.id, product]));
  const community = config.communityPolicy;
  const offline = config.offlinePolicy;
  if (offline.allowExport !== false) errors.push('PDF export must remain disabled.');
  if (config.accessPolicy.grantAccessFromClientSuccessScreen !== false) errors.push('Client success screens cannot grant book access.');
  if (community.maximumImageBytes < 0 || community.maximumImagesPerPost < 0) errors.push('Community image limits must be zero or greater.');
  if (config.physicalCommercePolicy.paymentMethods.some(method => !['bkash', 'visa'].includes(method))) errors.push('Only supported Bangladesh payment methods can be selected in this preview.');
  if (offline.accessLeaseDays < 1 || offline.onlineRefreshAfterDays < 1) errors.push('Offline access periods must be at least one day.');
  unique(types, 'Product type'); unique(stores, 'Storefront'); unique(categories, 'Category'); unique(pages, 'Page'); unique(products, 'Product');
  types.forEach(type => {
    if (!validId(type.id)) errors.push(`Invalid product type ID: ${type.id}`);
    localized(type.label, `Product type ${type.id}`);
    unique(type.attributes ?? [], `Attributes on ${type.id}`);
    (type.attributes ?? []).forEach(definition => {
      if (!/^[a-z][a-z0-9_]{1,39}$/.test(definition.id ?? '')) errors.push(`${type.id} has an invalid attribute ID.`);
      if (!['text', 'number', 'boolean', 'enum', 'text_list'].includes(definition.valueType)) errors.push(`${type.id}.${definition.id} has an unsupported field type.`);
      localized(definition.label, `Attribute ${definition.id}`);
      if (definition.valueType === 'enum' && (!definition.options?.length || new Set(definition.options.map(option => option.value)).size !== definition.options.length)) errors.push(`${type.id}.${definition.id} needs unique enum options.`);
    });
  });
  stores.forEach(store => {
    if (!validId(store.id)) errors.push(`Invalid storefront ID: ${store.id}`);
    localized(store.title, `Storefront ${store.id}`);
    if (store.enabled && !store.productTypeIds.some(id => types.some(type => type.id === id && type.enabled))) errors.push(`${store.title.en} needs at least one enabled product type.`);
    if (store.landingPageId && !pages.some(page => page.id === store.landingPageId && page.enabled)) errors.push(`${store.title.en} points to a missing or disabled landing page.`);
    store.categoryIds.forEach(id => { if (!categories.some(category => category.id === id && category.storefrontId === store.id)) errors.push(`${store.title.en} references a category that is missing or belongs to another store.`); });
  });
  categories.forEach(category => {
    if (!stores.some(store => store.id === category.storefrontId)) errors.push(`Category ${category.id} references an unknown storefront.`);
    localized(category.label, `Category ${category.id}`);
  });
  products.forEach(product => {
    if (!validId(product.id)) errors.push(`Invalid product ID: ${product.id}`);
    if (!types.some(type => type.id === product.typeId && type.enabled)) errors.push(`${product.title?.en ?? product.id} uses an unsupported product type.`);
    localized(product.title, `Product ${product.id}`); localized(product.description, `Product ${product.id} description`);
    product.categoryIds.forEach(id => { if (!categories.some(category => category.id === id)) errors.push(`${product.title.en} references a missing category.`); });
    const productType = types.find(type => type.id === product.typeId);
    (productType?.attributes ?? []).forEach(definition => {
      const value = product.attributes?.[definition.id];
      if (definition.required && (value === undefined || value === null || value === '' || (typeof value === 'string' && !value.trim()))) errors.push(`${product.title.en} needs ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'number' && (typeof value !== 'number' || !Number.isFinite(value))) errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'boolean' && typeof value !== 'boolean') errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'enum' && !(definition.options ?? []).some(option => option.value === value)) errors.push(`${product.title.en} has an unsupported ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'text_list' && !Array.isArray(value)) errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
      if (value !== undefined && ['text', 'enum'].includes(definition.valueType) && typeof value !== 'string') errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
    });
    if (!product.variants.length || product.variants.some(variant => variant.priceMinor < 0 || variant.currency !== 'BDT' || (variant.stockQuantity != null && variant.stockQuantity < 0))) errors.push(`${product.title.en} needs a valid BDT variant and stock value.`);
  });
  const supportedBlocks = new Set(['hero_v1', 'category_rail_v1', 'product_collection_v1', 'editorial_text_v1']);
  pages.forEach(page => {
    localized(page.title, `Page ${page.id}`);
    unique(page.blocks, `Blocks on ${page.id}`);
    page.blocks.forEach(block => {
      if (!supportedBlocks.has(block.type)) errors.push(`${page.title.en} contains an unsupported block.`);
      const blockCategories = block.categoryIds ?? [];
      blockCategories.forEach(id => { if (!categories.some(category => category.id === id)) errors.push(`${page.title.en} references a missing category.`); });
      const targetStore = block.query?.storefrontId;
      if (targetStore && !stores.some(store => store.id === targetStore)) errors.push(`${page.title.en} references a missing storefront.`);
      (block.query?.productIds ?? []).forEach(id => { if (!productsById.has(id)) errors.push(`${page.title.en} references a missing product.`); });
    });
  });
  return [...new Set(errors)];
}

function renderValidation() {
  const list = byId('validationList'); list.replaceChildren();
  const errors = validateDraft();
  const button = byId('exportDraftButton'); button.disabled = errors.length > 0;
  const messages = errors.length ? errors : ['Storefront, category, page, and block references are valid for this local preview.'];
  messages.forEach(message => { const item = text('li', message); if (errors.length) item.className = 'validation-error'; list.append(item); });
}

function exportDraft() {
  const errors = validateDraft();
  if (errors.length) { renderValidation(); return; }
  const blob = new Blob([JSON.stringify({ draftSchemaVersion: 1, baseConfigurationRevision: config.revision, configuration: config, products }, null, 2)], { type: 'application/json' });
  const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `littlepages-draft-revision-${config.revision ?? 1}.json`; link.click();
  URL.revokeObjectURL(link.href);
}

function addBlock() {
  const page = selectedPage(); if (!page) return;
  const type = byId('blockType').value; const id = newId('block');
  if (type === 'editorial_text_v1') page.blocks.push({ id, type, body: { en: 'Add your store message here.', bn: 'আপনার স্টোরের বার্তা লিখুন।' } });
  if (type === 'hero_v1') page.blocks.push({ id, type, title: { en: 'A little discovery', bn: 'নতুন কিছু আবিষ্কার' }, imageAssetId: null, action: null });
  if (type === 'category_rail_v1') page.blocks.push({ id, type, title: { en: 'Browse categories', bn: 'বিভাগ দেখুন' }, categoryIds: [] });
  if (type === 'product_collection_v1') page.blocks.push({ id, type, title: { en: 'Featured products', bn: 'বিশেষ পণ্য' }, query: {}, layout: 'grid' });
  renderPages();
}

function selectedPage() { return config.catalog.storePages.find(page => page.id === selectedPageId); }
function typeName(id) { return config.catalog.productTypes.find(type => type.id === id)?.label.en ?? id; }
function storeTitle(id) { return config.catalog.storefronts.find(store => store.id === id)?.title.en ?? id; }
function blockName(type) { return ({ editorial_text_v1: 'Editorial text', hero_v1: 'Hero banner', category_rail_v1: 'Category rail', product_collection_v1: 'Product collection' })[type] ?? 'Unsupported block'; }
function blockTitle(block) { return block.title?.en ?? block.body?.en ?? (block.query?.storefrontId ? `${storeTitle(block.query.storefrontId)} products · ${block.layout}` : 'Choose content in the page editor'); }
function slug(value) { return value.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '').slice(0, 55); }

loadConfig();
