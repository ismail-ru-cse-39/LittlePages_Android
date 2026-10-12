const LANGUAGE_KEY = 'littlepages-site-language-v1';
const CONTENT_CACHE_KEY = 'littlepages-site-publication-cache-v1';
let configuration;
let products = [];
let activeManifest;
let publicationRefreshPending = false;
let language = localStorage.getItem(LANGUAGE_KEY) === 'bn' ? 'bn' : 'en';
let currentStoreId = null;
let activeCategoryId = '';

const byId = id => document.getElementById(id);
const localized = value => typeof value === 'string' ? value : value?.[language] || value?.en || '';
const node = (tag, value, className) => { const element = document.createElement(tag); element.textContent = value ?? ''; if (className) element.className = className; return element; };

async function start() {
  try {
    await refreshPublication();
    currentStoreId = configuration.catalog.storefronts.find(store => store.enabled)?.id ?? null;
    bindEvents();
    render();
  } catch {
    const cached = readCachedPublication();
    if (cached) {
      try {
        validateManifest(cached.manifest);
        await validateSnapshot(cached.manifest, cached.snapshot);
        activatePublication(cached.manifest, cached.snapshot);
        currentStoreId = configuration.catalog.storefronts.find(store => store.enabled)?.id ?? null;
        bindEvents();
        render();
        bindPublicationRefresh();
        return;
      } catch { /* A damaged or incompatible cache is not activated. */ }
    }
    byId('pageContent').replaceChildren(node('div', language === 'bn' ? 'কনটেন্ট লোড হয়নি। স্থানীয় সার্ভার দিয়ে আবার চেষ্টা করুন।' : 'The preview content could not load. Please open the site through the local server.', 'empty-state'));
  }
  bindPublicationRefresh();
}

async function refreshPublication() {
  if (publicationRefreshPending) return;
  publicationRefreshPending = true;
  try {
  const manifestResponse = await fetch('../firebase/config/demo-manifest.v1.json', { cache: 'no-store' });
  if (!manifestResponse.ok) throw new Error('Could not load the publication manifest.');
  const manifest = await manifestResponse.json();
  validateManifest(manifest);
  if (activeManifest && manifest.publicationRevision < activeManifest.publicationRevision) throw new Error('The publication revision moved backwards.');
  const snapshotResponse = await fetch('../firebase/config/demo-publication.v1.json', { cache: 'no-store' });
  if (!snapshotResponse.ok) throw new Error('Could not load the published snapshot.');
  const snapshot = await snapshotResponse.json();
  await validateSnapshot(manifest, snapshot);
  activatePublication(manifest, snapshot);
  try { localStorage.setItem(CONTENT_CACHE_KEY, JSON.stringify({ manifest, snapshot })); } catch { /* The active snapshot remains usable if browser storage is full or disabled. */ }
  } finally {
    publicationRefreshPending = false;
  }
}

function bindPublicationRefresh() {
  document.addEventListener('visibilitychange', async () => {
    if (document.visibilityState !== 'visible') return;
    const previousRevision = activeManifest?.publicationRevision;
    try {
      await refreshPublication();
      if (activeManifest?.publicationRevision !== previousRevision) {
        if (!configuration.catalog.storefronts.some(store => store.id === currentStoreId && store.enabled)) {
          currentStoreId = configuration.catalog.storefronts.find(store => store.enabled)?.id ?? null;
        }
        activeCategoryId = '';
        render();
      }
    } catch { /* Keep the last complete validated snapshot during service or network failure. */ }
  });
}

function validateManifest(manifest) {
  if (manifest?.schemaVersion !== 1 || !Number.isInteger(manifest.publicationRevision) || manifest.publicationRevision < 1 ||
      !Number.isInteger(manifest.configurationRevision) || !Number.isInteger(manifest.catalogRevision) ||
      !Array.isArray(manifest.supportedLocales) || !manifest.supportedLocales.length ||
      !/^[a-f0-9]{64}$/.test(manifest.contentHash || '')) throw new Error('Unsupported publication manifest.');
}

async function validateSnapshot(manifest, snapshot) {
  if (snapshot?.schemaVersion !== 1 || snapshot.configuration?.schemaVersion !== 1 ||
      snapshot.configuration.revision !== manifest.configurationRevision || !Array.isArray(snapshot.products)) {
    throw new Error('The published snapshot does not match its manifest.');
  }
  const canonical = canonicalJson(snapshot);
  const bytes = new TextEncoder().encode(canonical);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const actualHash = [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
  if (actualHash !== manifest.contentHash) throw new Error('The published snapshot failed its integrity check.');
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  return JSON.stringify(value);
}

function activatePublication(manifest, snapshot) {
  configuration = snapshot.configuration;
  products = snapshot.products;
  activeManifest = manifest;
}

function readCachedPublication() {
  try {
    const cached = JSON.parse(localStorage.getItem(CONTENT_CACHE_KEY));
    if (cached?.manifest?.schemaVersion === 1 && cached?.snapshot?.configuration?.revision === cached.manifest.configurationRevision) return cached;
  } catch { /* Ignore malformed local cache and show the load error. */ }
  return null;
}

function bindEvents() {
  byId('languageButton').addEventListener('click', () => {
    language = language === 'en' ? 'bn' : 'en'; localStorage.setItem(LANGUAGE_KEY, language); render();
  });
  byId('menuButton').addEventListener('click', event => {
    const header = document.querySelector('.site-header');
    const open = header.classList.toggle('menu-open'); event.currentTarget.setAttribute('aria-expanded', String(open));
  });
  byId('closeDialog').addEventListener('click', () => byId('productDialog').close());
  byId('productDialog').addEventListener('click', event => { if (event.target === event.currentTarget) event.currentTarget.close(); });
}

function render() {
  if (!configuration) return;
  document.documentElement.lang = language;
  byId('languageButton').textContent = language === 'en' ? 'বাংলা' : 'English';
  byId('previewNotice').textContent = copy('Storefront preview · sample listings · checkout is not connected', 'স্টোরফ্রন্টের নমুনা · নমুনা পণ্য · কেনাকাটা এখনো চালু নয়');
  document.querySelector('.header-actions .button-soft').textContent = copy('For parents', 'অভিভাবকদের জন্য');
  document.querySelector('.header-actions .button-primary').textContent = copy('Explore books', 'বই দেখুন');
  document.querySelector('.parent-note .eyebrow').textContent = copy('MADE FOR GROWING TOGETHER', 'একসঙ্গে বেড়ে ওঠার জন্য');
  document.querySelector('.parent-note h2').textContent = copy('A little story can make a big moment.', 'একটি ছোট গল্পও বড় মুহূর্ত তৈরি করতে পারে।');
  document.querySelector('.parent-note p').textContent = copy('Thoughtful reading experiences help families make room for curiosity, kindness, and imagination.', 'যত্নে তৈরি পড়ার অভিজ্ঞতা পরিবারে কৌতূহল, দয়া ও কল্পনার জায়গা করে দেয়।');
  document.querySelector('footer>span:nth-child(2)').textContent = copy('Made with care for little readers and their grown-ups.', 'ছোট পাঠক ও তাদের পরিবারের জন্য যত্নে তৈরি।');
  document.querySelector('.footer-language').textContent = copy('English · বাংলা', 'বাংলা · English');
  renderNavigation(); renderStoreTabs(); renderStorePage();
}

function renderNavigation() {
  const navigation = byId('storeNavigation'); navigation.replaceChildren();
  const activeStores = configuration.catalog.storefronts.filter(store => store.enabled && store.navigationVisible).sort((a, b) => a.sortOrder - b.sortOrder);
  activeStores.forEach(store => {
    const link = node('a', localized(store.title), `nav-link${store.id === currentStoreId ? ' active' : ''}`);
    link.href = `#${encodeURIComponent(store.slug)}`;
    link.addEventListener('click', event => { event.preventDefault(); selectStore(store.id); document.querySelector('.site-header').classList.remove('menu-open'); byId('menuButton').setAttribute('aria-expanded', 'false'); });
    navigation.append(link);
  });
}

function renderStoreTabs() {
  const tabs = byId('storeTabs'); tabs.replaceChildren();
  configuration.catalog.storefronts.filter(store => store.enabled).sort((a, b) => a.sortOrder - b.sortOrder).forEach(store => {
    const button = node('button', localized(store.title), `store-tab${store.id === currentStoreId ? ' active' : ''}`);
    button.type = 'button'; button.addEventListener('click', () => selectStore(store.id)); tabs.append(button);
  });
}

function selectStore(storeId) {
  currentStoreId = storeId; activeCategoryId = ''; render(); window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderStorePage() {
  const content = byId('pageContent'); content.replaceChildren();
  const store = configuration.catalog.storefronts.find(item => item.id === currentStoreId && item.enabled);
  if (!store) { content.append(node('div', localized({ en: 'This storefront is not available yet.', bn: 'এই স্টোরটি এখনো চালু হয়নি।' }), 'empty-state')); return; }
  const page = configuration.catalog.storePages.find(item => item.id === store.landingPageId && item.enabled);
  const pageTitle = localized(page?.title) || localized(store.title);
  const description = localized(store.description) || copy('A thoughtful collection for curious little minds.', 'কৌতূহলী ছোট্ট মনগুলোর জন্য যত্নে সাজানো সংগ্রহ।');
  content.append(renderHero(store, pageTitle, description));
  const blocks = page?.blocks ?? [];
  if (!blocks.length) content.append(renderCollection(store, null));
  blocks.forEach(block => {
    switch (block.type) {
      case 'editorial_text_v1': content.append(renderEditorial(block)); break;
      case 'category_rail_v1': content.append(renderCategoryRail(store, block)); break;
      case 'product_collection_v1': content.append(renderCollection(store, block)); break;
      case 'hero_v1': content.append(renderBlockHero(block)); break;
      default: break; // Unregistered blocks are ignored safely in this renderer.
    }
  });
}

function renderHero(store, title, description) {
  const section = document.createElement('section'); section.className = 'hero';
  const copyArea = document.createElement('div'); copyArea.className = 'hero-copy';
  copyArea.append(node('span', copy('A LITTLE MORE WONDER', 'একটু বেশি বিস্ময়'), 'eyebrow'));
  copyArea.append(node('h1', title));
  copyArea.append(node('p', description));
  const action = node('a', copy('Browse collection', 'সংগ্রহ দেখুন'), 'button button-primary'); action.href = '#collection'; copyArea.append(action);
  const art = document.createElement('div'); art.className = 'hero-art'; art.setAttribute('aria-hidden', 'true');
  const firstType = configuration.catalog.productTypes.find(type => store.productTypeIds.includes(type.id));
  const heroSymbol = firstType?.renderer === 'book_v1' ? '🦊' : '🧸';
  art.append(node('span', '', 'sun'), node('span', '', 'hill'), node('span', '', 'hill two'), node('span', heroSymbol, 'fox'), node('span', '✿', 'leaf one'), node('span', '❋', 'leaf two'));
  section.append(copyArea, art); return section;
}

function renderEditorial(block) {
  const section = document.createElement('section'); section.className = 'section-heading editorial-heading';
  section.append(node('div', localized(block.body), 'editorial-copy'));
  return section;
}

function renderBlockHero(block) {
  const section = document.createElement('section'); section.className = 'editorial-banner';
  section.append(node('span', '✿', 'editorial-mark'), node('h2', localized(block.title)));
  return section;
}

function renderCategoryRail(store, block) {
  const allCategories = activeCategories(store);
  const selectedIds = block.categoryIds?.length ? new Set(block.categoryIds) : null;
  const categories = selectedIds ? allCategories.filter(category => selectedIds.has(category.id)) : allCategories;
  const section = document.createElement('section'); section.className = 'category-section';
  const heading = document.createElement('div'); heading.className = 'section-heading';
  heading.append(node('div', localized(block.title) || copy('Find a favorite', 'পছন্দের গল্প খুঁজুন'))); section.append(heading);
  const rail = document.createElement('div'); rail.className = 'category-rail';
  const all = node('button', copy('All', 'সব'), `category-chip${!activeCategoryId ? ' active' : ''}`); all.type = 'button'; all.addEventListener('click', () => { activeCategoryId = ''; renderStorePage(); }); rail.append(all);
  categories.forEach(category => {
    const button = node('button', localized(category.label), `category-chip${activeCategoryId === category.id ? ' active' : ''}`); button.type = 'button';
    button.addEventListener('click', () => { activeCategoryId = activeCategoryId === category.id ? '' : category.id; renderStorePage(); }); rail.append(button);
  });
  section.append(rail); return section;
}

function renderCollection(store, block) {
  const section = document.createElement('section'); section.id = 'collection';
  const heading = document.createElement('div'); heading.className = 'section-heading';
  const title = localized(block?.title) || copy('A story for every little reader', 'প্রতিটি ছোট পাঠকের জন্য গল্প');
  const headingCopy = document.createElement('div'); headingCopy.append(node('span', copy('A LITTLE SHELF OF WONDERS', 'গল্পের ছোট্ট তাক'), 'eyebrow'), node('h2', title));
  heading.append(headingCopy, node('p', copy('Thoughtfully chosen for reading together.', 'একসঙ্গে পড়ার জন্য যত্নে বেছে নেওয়া।'))); section.append(heading);
  const listed = products.filter(product => product.status === 'published' && store.productTypeIds.includes(product.typeId));
  const blockCategories = block?.query?.categoryIds ?? [];
  const productIds = block?.query?.productIds ?? [];
  const filtered = listed.filter(product => {
    const matchesStoreCategory = !store.categoryIds.length || product.categoryIds.some(id => store.categoryIds.includes(id));
    const matchesBlockCategory = !blockCategories.length || product.categoryIds.some(id => blockCategories.includes(id));
    const matchesSelected = !activeCategoryId || product.categoryIds.includes(activeCategoryId);
    const matchesProductIds = !productIds.length || productIds.includes(product.id);
    const matchesTypes = !(block?.query?.productTypeIds?.length) || block.query.productTypeIds.includes(product.typeId);
    return matchesStoreCategory && matchesBlockCategory && matchesSelected && matchesProductIds && matchesTypes;
  });
  const grid = document.createElement('div'); grid.className = 'product-grid';
  filtered.forEach(product => grid.append(renderProductCard(product)));
  if (filtered.length) section.append(grid); else section.append(node('div', copy('No stories in this category yet. Choose another topic to explore.', 'এই বিভাগে এখনো গল্প নেই। অন্য বিষয় বেছে দেখুন।'), 'empty-state'));
  return section;
}

function renderProductCard(product) {
  const card = document.createElement('article'); card.className = 'product-card'; card.tabIndex = 0; card.setAttribute('role', 'button'); card.setAttribute('aria-label', `${localized(product.title)}, ${copy('view details', 'বিস্তারিত দেখুন')}`);
  const art = document.createElement('div'); art.className = 'product-art';
  const isBook = configuration.catalog.productTypes.find(type => type.id === product.typeId)?.renderer === 'book_v1';
  if (isBook) {
    const cover = document.createElement('div'); cover.className = 'book-cover';
    cover.append(node('small', 'LITTLEPAGES'), node('span', artwork(product.id), 'cover-animal'), node('b', localized(product.title)));
    art.append(node('span', copy('IN APP', 'অ্যাপে'), 'badge'), cover);
  } else {
    art.classList.add('physical-product-art');
    art.append(node('span', artwork(product.id), 'physical-product-symbol'), node('span', copy('PHYSICAL ITEM', 'বাস্তব পণ্য'), 'badge'));
  }
  const details = document.createElement('div'); details.className = 'card-body';
  const author = product.attributes?.author || localized(configuration.catalog.productTypes.find(type => type.id === product.typeId)?.label) || '';
  details.append(node('div', author, 'card-meta'), node('h3', localized(product.title)), node('p', localized(product.description)));
  const price = product.variants?.[0]?.priceMinor;
  const foot = document.createElement('div'); foot.className = 'card-foot';
  foot.append(node('span', isBook ? copy('Read in app', 'অ্যাপে পড়ুন') : price > 0 ? `৳${(price / 100).toLocaleString(language === 'bn' ? 'bn-BD' : 'en-BD')}` : copy('View item', 'পণ্য দেখুন'), 'read-label'), node('span', '↗', 'round-arrow'));
  details.append(foot); card.append(art, details);
  const open = () => showProduct(product); card.addEventListener('click', open); card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); } });
  return card;
}

function showProduct(product) {
  const details = byId('productDetails'); details.replaceChildren();
  details.append(node('div', artwork(product.id), 'dialog-cover'));
  const isBook = configuration.catalog.productTypes.find(type => type.id === product.typeId)?.renderer === 'book_v1';
  details.append(node('span', product.attributes?.author || localized(configuration.catalog.productTypes.find(type => type.id === product.typeId)?.label) || copy('LITTLEPAGES STORY', 'লিটলপেজেসের গল্প'), 'dialog-meta'));
  details.append(node('h2', localized(product.title)), node('p', localized(product.description)));
  const age = product.attributes?.age_band ?? product.attributes?.ageBand;
  const pageCount = product.attributes?.page_count ?? product.attributes?.pageCount;
  if (age || pageCount) details.append(node('div', [age ? `${copy('Ages', 'বয়স')} ${age}` : '', pageCount ? `${pageCount} ${copy('pages', 'পৃষ্ঠা')}` : ''].filter(Boolean).join(' · '), 'dialog-meta'));
  if (!isBook && product.variants?.[0]) details.append(node('div', `${copy('Price', 'মূল্য')}: ৳${(product.variants[0].priceMinor / 100).toLocaleString(language === 'bn' ? 'bn-BD' : 'en-BD')}`, 'dialog-meta'));
  details.append(node('div', copy('This website is a preview. Reading access and checkout will be available when the shared account and publishing services are connected.', 'এটি ওয়েবসাইটের নমুনা। শেয়ার করা অ্যাকাউন্ট ও প্রকাশনা সেবা চালু হলে পড়া ও কেনাকাটা পাওয়া যাবে।'), 'dialog-note'));
  byId('productDialog').showModal();
}

function activeCategories(store) {
  const ids = new Set(store.categoryIds);
  return configuration.catalog.productCategories.filter(category => category.enabled && category.storefrontId === store.id && (!ids.size || ids.has(category.id))).sort((a, b) => a.sortOrder - b.sortOrder);
}
function copy(english, bangla) { return language === 'bn' ? bangla : english; }
function artwork(id) { return ({ 'brave-little-fox': '🦊', 'day-at-the-lake': '🐻', 'kind-giant': '🌿', 'woodland-blocks': '🧸' })[id] || '📚'; }

start();
