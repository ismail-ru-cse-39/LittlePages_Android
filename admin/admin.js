const STORAGE_KEY = 'littlepages-admin-preview-draft-v1';
const fallback = {
  schemaVersion: 1,
  revision: 1,
  catalog: {
    productTypes: [
      { id: 'book', label: { en: 'Books', bn: 'বই' }, renderer: 'book_v1', enabled: true },
      { id: 'toy', label: { en: 'Toys', bn: 'খেলনা' }, renderer: 'physical_good_v1', enabled: true }
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
let selectedPageId = '';
let eventsBound = false;
const byId = id => document.getElementById(id);
const clone = value => JSON.parse(JSON.stringify(value));
const newId = prefix => `${prefix}-${Math.random().toString(36).slice(2, 8)}`;
const text = (tag, value, className) => { const node = document.createElement(tag); node.textContent = value ?? ''; if (className) node.className = className; return node; };

async function loadConfig() {
  try {
    const response = await fetch('../firebase/config/provisional-defaults.v1.json');
    if (!response.ok) throw new Error('defaults unavailable');
    config = await response.json();
  } catch {
    config = clone(fallback);
  }
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved) {
    try { config = JSON.parse(saved); } catch { localStorage.removeItem(STORAGE_KEY); }
  }
  config.catalog.productTypes ??= clone(fallback.catalog.productTypes);
  config.catalog.storefronts ??= clone(fallback.catalog.storefronts);
  config.catalog.productCategories ??= clone(fallback.catalog.productCategories);
  config.catalog.storePages ??= clone(fallback.catalog.storePages);
  selectedPageId = config.catalog.storePages[0]?.id ?? '';
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
    const id = slug(titleEn);
    if (!titleEn || !titleBn || config.catalog.storefronts.some(store => store.id === id)) return;
    config.catalog.storefronts.push({ id, slug: id, title: { en: titleEn, bn: titleBn }, productTypeIds: [typeId], categoryIds: [], landingPageId: null, navigationVisible: true, enabled: true, sortOrder: (config.catalog.storefronts.length + 1) * 10, regions: ['BD'] });
    event.currentTarget.reset(); event.currentTarget.hidden = true; render();
  });
  byId('categoryForm').addEventListener('submit', event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const storeId = data.get('storeId');
    const labelEn = data.get('labelEn').trim();
    const labelBn = data.get('labelBn').trim();
    const id = slug(`${storeId}-${labelEn}`);
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
  byId('addBlockButton').addEventListener('click', addBlock);
  byId('publishButton').addEventListener('click', () => { renderValidation(); byId('publishDialog').showModal(); });
  byId('closeDialogButton').addEventListener('click', () => byId('publishDialog').close());
  byId('saveDraftButton').addEventListener('click', () => { localStorage.setItem(STORAGE_KEY, JSON.stringify(config)); byId('publishDialog').close(); render(); });
  byId('exportDraftButton').addEventListener('click', exportDraft);
  byId('resetButton').addEventListener('click', async () => { localStorage.removeItem(STORAGE_KEY); await loadConfig(); });
}

function render() {
  renderSummary(); renderStores(); renderCategories(); renderPages(); renderProductTypes();
  byId('revisionValue').textContent = `Draft ${config.revision ?? 1}`;
}

function renderSummary() {
  byId('storeCount').textContent = config.catalog.storefronts.filter(store => store.enabled).length;
  byId('categoryCount').textContent = config.catalog.productCategories.filter(category => category.enabled).length;
  byId('pageCount').textContent = config.catalog.storePages.filter(page => page.enabled).length;
}

function renderStores() {
  const list = byId('storeList'); list.replaceChildren();
  [...config.catalog.storefronts].sort((a, b) => a.sortOrder - b.sortOrder).forEach(store => {
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
    list.append(row);
  });
}

function renderCategories() {
  const select = byId('categoryStoreSelect'); select.replaceChildren();
  config.catalog.storefronts.forEach(store => { const option = document.createElement('option'); option.value = store.id; option.textContent = store.title.en; select.append(option); });
  const list = byId('categoryList'); list.replaceChildren();
  config.catalog.productCategories.forEach(category => {
    const row = document.createElement('div'); row.className = 'category-row';
    const labels = text('div', category.label.en); labels.append(text('small', `${category.label.bn} · ${storeTitle(category.storefrontId)}`)); row.append(labels);
    const remove = text('button', 'Remove', 'remove-button'); remove.type = 'button'; remove.addEventListener('click', () => {
      config.catalog.productCategories = config.catalog.productCategories.filter(item => item.id !== category.id);
      config.catalog.storefronts.forEach(store => { store.categoryIds = store.categoryIds.filter(id => id !== category.id); }); render();
    }); row.append(remove); list.append(row);
  });
  const submit = byId('categoryForm').querySelector('button[type="submit"]'); submit.disabled = config.catalog.storefronts.length === 0;
}

function renderPages() {
  const pages = config.catalog.storePages;
  const select = byId('pageSelect'); select.replaceChildren();
  pages.forEach(page => { const option = document.createElement('option'); option.value = page.id; option.textContent = page.title.en; select.append(option); });
  if (!pages.some(page => page.id === selectedPageId)) selectedPageId = pages[0]?.id ?? '';
  select.value = selectedPageId;
  const page = selectedPage(); byId('pageTitleInput').value = page?.title.en ?? '';
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
}

function renderProductTypes() {
  const list = byId('productTypeList'); list.replaceChildren();
  config.catalog.productTypes.forEach(type => {
    const card = document.createElement('div'); card.className = 'type-card'; card.append(text('span', type.renderer === 'book_v1' ? '▤' : '◇', 'store-icon'));
    const details = text('div', type.label.en); details.className = 'store-name'; details.append(text('small', `${type.renderer} · supported template`)); card.append(details); list.append(card);
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
  unique(types, 'Product type'); unique(stores, 'Storefront'); unique(categories, 'Category'); unique(pages, 'Page');
  types.forEach(type => { if (!validId(type.id)) errors.push(`Invalid product type ID: ${type.id}`); localized(type.label, `Product type ${type.id}`); });
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
  const blob = new Blob([JSON.stringify(config, null, 2)], { type: 'application/json' });
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
