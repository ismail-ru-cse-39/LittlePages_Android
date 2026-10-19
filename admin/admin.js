import { callAdmin, connectFirebaseAdmin, readCurrentPublication, saveFirebaseAdminSettings, signInAdmin, signOutAdmin, watchAdminAuth } from './firebase-admin.js';

const STORAGE_KEY = 'littlepages-admin-preview-draft-v1';
const STAFF_STORAGE_KEY = 'littlepages-admin-staff-role-preview-v1';
const HISTORY_STORAGE_KEY = 'littlepages-admin-preview-history-v1';
const MODERATION_STORAGE_KEY = 'littlepages-admin-moderation-preview-v1';
const PROMOTION_AUDIT_KEY = 'littlepages-admin-promotion-audit-preview-v1';
const BOOK_ASSET_STORAGE_KEY = 'littlepages-admin-book-asset-manifest-v1';
const BOOK_ASSET_HISTORY_KEY = 'littlepages-admin-book-asset-history-v1';
const POLICY_REVIEW_KEY = 'littlepages-admin-policy-review-preview-v1';
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
        { id: 'author', label: { en: 'Author', bn: 'লেখক' }, valueType: 'text', required: true }
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
let promotions = [];
let discountCodes = [];
let bookAssetManifest = [];
let bookAssetHistory = [];
let staffRoleDraft = [];
let draftHistory = [];
let moderationPreview;
let promotionAudit = [];
let policyReview = { baseline: null, history: [], events: [], approval: 'pending', impactPreview: null };
let selectedPageId = '';
let eventsBound = false;
let firebaseClient = null;
let firebaseDraft;
try { firebaseDraft = JSON.parse(localStorage.getItem('littlepages-admin-server-draft-v1') || 'null'); } catch { firebaseDraft = null; }
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
      if (draft.configuration) { config = draft.configuration; products = draft.products ?? products; promotions = draft.promotions ?? []; discountCodes = draft.discountCodes ?? []; }
      if (draft.configuration) { bookAssetManifest = draft.bookAssetManifest ?? []; bookAssetHistory = draft.bookAssetHistory ?? []; }
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
  try { promotionAudit = JSON.parse(localStorage.getItem(PROMOTION_AUDIT_KEY) || '[]'); } catch { promotionAudit = []; }
  try { policyReview = { ...policyReview, ...JSON.parse(localStorage.getItem(POLICY_REVIEW_KEY) || '{}') }; } catch { policyReview = { baseline: null, history: [], events: [], approval: 'pending', impactPreview: null }; }
  policyReview.history ??= []; policyReview.events ??= [];
  if (!bookAssetManifest.length) { try { bookAssetManifest = JSON.parse(localStorage.getItem(BOOK_ASSET_STORAGE_KEY) || '[]'); } catch { bookAssetManifest = []; } }
  if (!bookAssetHistory.length) { try { bookAssetHistory = JSON.parse(localStorage.getItem(BOOK_ASSET_HISTORY_KEY) || '[]'); } catch { bookAssetHistory = []; } }
  moderationPreview ??= { reports: clone(sampleModerationReports), events: [] };
  policyReview.baseline ??= policySnapshot();
  savePolicyReview();
  bindEvents();
  render();
  initializeFirebaseAdmin();
}

function bindEvents() {
  if (eventsBound) return;
  eventsBound = true;
  byId('firebaseConfigForm').addEventListener('submit', async event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    try {
      const configValue = JSON.parse(data.get('config'));
      if (!configValue.apiKey || !configValue.projectId || !configValue.appId) throw new Error('The Web app config needs apiKey, projectId, and appId.');
      saveFirebaseAdminSettings(configValue, data.get('siteKey').trim());
      firebaseClient = await connectFirebaseAdmin();
      enableFirebaseControls(false);
      byId('firebaseSignInForm').hidden = false;
      setFirebaseStatus('Firebase connected. Sign in with an authorized staff account.');
      await refreshFirebasePublication();
    } catch (error) { setFirebaseStatus(`Connection failed: ${error.message}`, true); }
  });
  byId('firebaseSignInForm').addEventListener('submit', async event => {
    event.preventDefault();
    if (!firebaseClient) return;
    const data = new FormData(event.currentTarget);
    try {
      const credential = await signInAdmin(firebaseClient, data.get('email'), data.get('password'));
      enableFirebaseControls(true);
      byId('refreshModerationButton').disabled = false;
      byId('refreshCodeCampaignsButton').disabled = false;
      setFirebaseStatus(`Signed in as ${credential.user.email ?? credential.user.uid}. Role checks are enforced by Firebase Functions.`);
      refreshFirebasePublication(); refreshLiveModeration(); refreshServerCodeCampaigns();
    }
    catch (error) { setFirebaseStatus(`Sign-in failed: ${error.message}`, true); }
  });
  byId('firebaseSignOutButton').addEventListener('click', async () => { if (firebaseClient) { await signOutAdmin(firebaseClient); enableFirebaseControls(false); byId('refreshModerationButton').disabled = true; byId('refreshCodeCampaignsButton').disabled = true; } });
  byId('firebaseRefreshButton').addEventListener('click', refreshFirebasePublication);
  byId('firebaseSaveDraftButton').addEventListener('click', saveFirebaseDraft);
  byId('firebaseValidateButton').addEventListener('click', validateFirebaseDraft);
  byId('firebaseApproveButton').addEventListener('click', approveFirebaseDraft);
  byId('firebasePublishButton').addEventListener('click', publishFirebaseDraft);
  byId('firebaseRollbackButton').addEventListener('click', rollbackFirebasePublication);
  byId('firebaseSetRoleButton').addEventListener('click', setFirebaseStaffRole);
  byId('refreshModerationButton').addEventListener('click', refreshLiveModeration);
  byId('refreshCodeCampaignsButton').addEventListener('click', refreshServerCodeCampaigns);
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
  byId('productForm').elements.typeId.addEventListener('change', () => { renderAttributeFields(); renderProductCategoryOptions(); toggleBookDetails(); });
  byId('addVariantButton').addEventListener('click', () => {
    const values = readVariantRows(false);
    if (values.length >= 20) { window.alert('A local product draft can have up to 20 variants.'); return; }
    const isBook = config.catalog.productTypes.find(type => type.id === byId('productForm').elements.typeId.value)?.renderer === 'book_v1';
    values.push({ id: newId('variant'), label: { en: isBook ? 'Read in app' : 'Standard', bn: isBook ? 'অ্যাপে পড়ুন' : 'সাধারণ' }, priceMinor: 0, currency: 'BDT', stockState: isBook ? 'not_applicable' : 'out_of_stock', stockQuantity: null });
    renderVariantEditor(values);
  });
  byId('promotionForm').elements.scope.addEventListener('change', event => updateTargetFields(event.currentTarget, 'promotion-target-field'));
  byId('discountCodeForm').elements.scope.addEventListener('change', event => updateTargetFields(event.currentTarget, 'code-target-field'));
  byId('promotionForm').elements.destinationKind.addEventListener('change', () => populatePromotionTargets());
  byId('discountCodeForm').elements.mode.addEventListener('change', updateDiscountCodeMode);
  byId('discountCodeForm').elements.eligibility.addEventListener('change', event => { byId('assignedCustomersField').hidden = event.currentTarget.value !== 'assigned_account'; });
  byId('promotionForm').addEventListener('submit', savePromotion);
  byId('discountCodeForm').addEventListener('submit', saveDiscountCode);
  byId('bookAssetForm').addEventListener('submit', stageBookAssets);
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
  byId('exportPublicationPayloadButton').addEventListener('click', exportPublicationPayload);
  byId('resetButton').addEventListener('click', async () => { localStorage.removeItem(STORAGE_KEY); localStorage.removeItem(STAFF_STORAGE_KEY); localStorage.removeItem(HISTORY_STORAGE_KEY); localStorage.removeItem(MODERATION_STORAGE_KEY); localStorage.removeItem(PROMOTION_AUDIT_KEY); localStorage.removeItem(BOOK_ASSET_STORAGE_KEY); localStorage.removeItem(BOOK_ASSET_HISTORY_KEY); localStorage.removeItem(POLICY_REVIEW_KEY); staffRoleDraft = []; draftHistory = []; moderationPreview = null; promotionAudit = []; policyReview = { baseline: null, history: [], events: [], approval: 'pending', impactPreview: null }; bookAssetManifest = []; bookAssetHistory = []; promotions = []; discountCodes = []; await loadConfig(); });
  byId('previewPolicyImpactButton').addEventListener('click', previewPolicyImpact);
  byId('approvePolicyPreviewButton').addEventListener('click', approvePolicyPreview);
  byId('savePolicyRevisionButton').addEventListener('click', savePolicyRevision);
}

async function initializeFirebaseAdmin() {
  try {
    firebaseClient = await connectFirebaseAdmin();
    if (!firebaseClient) return;
    byId('firebaseSignInForm').hidden = false;
    enableFirebaseControls(false);
    watchAdminAuth(firebaseClient, user => {
      const signedIn = Boolean(user);
      enableFirebaseControls(signedIn);
      byId('refreshModerationButton').disabled = !signedIn;
      byId('refreshCodeCampaignsButton').disabled = !signedIn;
      setFirebaseStatus(signedIn ? `Signed in as ${user.email ?? user.uid}. Role checks are enforced by Firebase Functions.` : 'Firebase connected. Sign in with an authorized staff account.');
      if (signedIn) { refreshFirebasePublication(); refreshLiveModeration(); refreshServerCodeCampaigns(); }
    });
  } catch (error) { setFirebaseStatus(`Firebase setup unavailable: ${error.message}`, true); }
}

function enableFirebaseControls(enabled) {
  ['firebaseRefreshButton', 'firebaseSaveDraftButton', 'firebaseValidateButton', 'firebaseApproveButton', 'firebasePublishButton', 'firebaseRollbackButton', 'firebaseSetRoleButton'].forEach(id => { byId(id).disabled = !enabled; });
  const form = byId('firebaseSignInForm');
  form.hidden = !firebaseClient;
  form.elements.email.disabled = enabled;
  form.elements.password.disabled = enabled;
  form.querySelector('button[type="submit"]').hidden = enabled;
  byId('firebaseSignOutButton').hidden = !enabled;
}

function setFirebaseStatus(message, isError = false) {
  const status = byId('firebaseConnectionStatus');
  status.textContent = message;
  status.dataset.state = isError ? 'error' : 'ok';
}

async function refreshFirebasePublication() {
  if (!firebaseClient) return;
  try {
    const manifest = await readCurrentPublication(firebaseClient);
    byId('basePublicationRevisionInput').value = manifest.publicationRevision;
    setFirebaseStatus(`Firebase connected · publication revision ${manifest.publicationRevision}${manifest.contentHash ? ` · ${manifest.contentHash.slice(0, 12)}…` : ' · no published snapshot yet'}.`);
  } catch (error) { setFirebaseStatus(`Publication read failed: ${error.message}`, true); }
}

async function saveFirebaseDraft() {
  if (!firebaseClient) return;
  const errors = validateDraft();
  if (errors.length) { renderValidation(); setFirebaseStatus('Fix local validation errors before saving.', true); return; }
  try {
    const manifest = await readCurrentPublication(firebaseClient);
    const result = await callAdmin(firebaseClient, 'saveConfigurationDraft', {
      ...(firebaseDraft ? { draftId: firebaseDraft.draftId, expectedDraftRevision: firebaseDraft.draftRevision } : {}),
      basePublicationRevision: manifest.publicationRevision,
      snapshot: { configuration: { ...clone(config), status: 'provisional' }, products: clone(products), promotions: clone(promotions) },
      minimumClientVersions: {}
    });
    firebaseDraft = { draftId: result.draftId, draftRevision: result.draftRevision, basePublicationRevision: result.basePublicationRevision };
    localStorage.setItem('littlepages-admin-server-draft-v1', JSON.stringify(firebaseDraft));
    setFirebaseStatus(`Server draft ${result.draftId} saved at draft revision ${result.draftRevision}.`);
    renderValidation();
  } catch (error) { setFirebaseStatus(`Server draft save failed: ${error.message}`, true); }
}

async function validateFirebaseDraft() {
  if (!firebaseClient || !firebaseDraft) return setFirebaseStatus('Save a server draft first.', true);
  try {
    const result = await callAdmin(firebaseClient, 'validateConfigurationDraft', { draftId: firebaseDraft.draftId });
    setFirebaseStatus(result.valid ? `Server validation passed. Owner review blockers: ${result.approvalBlockers.length}.` : `Server validation found ${result.errors.length} issue(s): ${result.errors.join(' ')}`, !result.valid);
  } catch (error) { setFirebaseStatus(`Server validation failed: ${error.message}`, true); }
}

async function approveFirebaseDraft() {
  if (!firebaseClient || !firebaseDraft) return setFirebaseStatus('Save a server draft first.', true);
  const reason = window.prompt('Approval reason. Confirm the required content, rights, policy, and legal reviews are complete:');
  if (!reason?.trim()) return;
  try {
    const result = await callAdmin(firebaseClient, 'approveConfigurationDraft', { draftId: firebaseDraft.draftId, reason: reason.trim() });
    const preview = await callAdmin(firebaseClient, 'getConfigurationDraftPreview', { draftId: firebaseDraft.draftId });
    firebaseDraft.draftRevision = preview.draftRevision;
    localStorage.setItem('littlepages-admin-server-draft-v1', JSON.stringify(firebaseDraft));
    setFirebaseStatus(`Draft approved · ${result.contentHash.slice(0, 12)}… Publish is a separate action.`);
  } catch (error) { setFirebaseStatus(`Approval failed: ${error.message}`, true); }
}

async function publishFirebaseDraft() {
  if (!firebaseClient || !firebaseDraft) return setFirebaseStatus('Save and approve a server draft first.', true);
  const reason = window.prompt('Publication reason:');
  if (!reason?.trim()) return;
  try {
    const preview = await callAdmin(firebaseClient, 'getConfigurationDraftPreview', { draftId: firebaseDraft.draftId });
    const result = await callAdmin(firebaseClient, 'publishConfigurationDraft', { draftId: firebaseDraft.draftId, expectedDraftRevision: preview.draftRevision, reason: reason.trim() });
    firebaseDraft = null;
    localStorage.removeItem('littlepages-admin-server-draft-v1');
    setFirebaseStatus(`Published revision ${result.publicationRevision}.`);
    await refreshFirebasePublication();
  } catch (error) { setFirebaseStatus(`Publish failed: ${error.message}`, true); }
}

async function rollbackFirebasePublication() {
  const targetPublicationRevision = Number(byId('rollbackTargetRevision').value);
  if (!Number.isSafeInteger(targetPublicationRevision) || targetPublicationRevision < 1) return setFirebaseStatus('Enter a valid earlier publication revision.', true);
  if (!window.confirm(`Create a new publication that restores revision ${targetPublicationRevision}?`)) return;
  const reason = window.prompt('Rollback reason:');
  if (!reason?.trim()) return;
  try {
    const result = await callAdmin(firebaseClient, 'rollbackConfigurationPublication', { targetPublicationRevision, reason: reason.trim() });
    setFirebaseStatus(`Rolled back by publishing revision ${result.publicationRevision} from snapshot ${targetPublicationRevision}.`);
    await refreshFirebasePublication();
  } catch (error) { setFirebaseStatus(`Rollback failed: ${error.message}`, true); }
}

async function setFirebaseStaffRole() {
  const targetUid = byId('staffTargetUid').value.trim();
  if (!targetUid) return setFirebaseStatus('Enter an existing Firebase Auth user UID.', true);
  const reason = window.prompt('Reason for this staff role change:');
  if (!reason?.trim()) return;
  try {
    const result = await callAdmin(firebaseClient, 'setStaffRole', { targetUid, role: byId('staffTargetRole').value, status: byId('staffTargetStatus').value, reason: reason.trim() });
    setFirebaseStatus(`Updated ${result.targetUid}: ${result.role} · ${result.status}.`);
  } catch (error) { setFirebaseStatus(`Role update failed: ${error.message}`, true); }
}

async function refreshLiveModeration() {
  const root = byId('liveModerationQueue');
  root.replaceChildren();
  if (!firebaseClient) return;
  byId('liveModerationStatus').textContent = 'Loading reports…';
  try {
    const result = await callAdmin(firebaseClient, 'listModerationReports', { limit: 100 });
    if (!result.reports.length) { byId('liveModerationStatus').textContent = 'No open reports.'; return; }
    byId('liveModerationStatus').textContent = `${result.reports.length} open report(s).`;
    result.reports.forEach(report => {
      const card = document.createElement('article'); card.className = 'moderation-card';
      card.append(text('span', `${report.targetType ?? 'content'} · ${report.reason ?? 'No reason supplied'} · ${report.targetId ?? 'unknown target'}`, 'eyebrow'));
      if (report.targetPreview?.body) card.append(text('p', report.targetPreview.body));
      if (report.targetPreview?.moderationState) card.append(text('small', `Current state: ${report.targetPreview.moderationState}`));
      if (report.createdAt) card.append(text('small', `Reported ${new Date(report.createdAt?.seconds ? report.createdAt.seconds * 1000 : report.createdAt).toLocaleString()}`));
      const actions = document.createElement('div'); actions.className = 'moderation-actions';
      [['keep', 'Keep visible'], ['hide', 'Hide'], ['remove', 'Remove']].forEach(([action, label]) => {
        const button = text('button', label, action === 'remove' ? 'button button-soft' : 'button button-primary'); button.type = 'button';
        button.addEventListener('click', async () => {
          const reason = window.prompt(`Reason for ${action} decision:`);
          if (!reason?.trim()) return;
          button.disabled = true;
          try { await callAdmin(firebaseClient, 'moderateCommunityItem', { reportId: report.id, action, reason: reason.trim() }); await refreshLiveModeration(); }
          catch (error) { button.disabled = false; byId('liveModerationStatus').textContent = `Moderation failed: ${error.message}`; }
        }); actions.append(button);
      });
      card.append(actions); root.append(card);
    });
  } catch (error) { byId('liveModerationStatus').textContent = `Could not load reports: ${error.message}`; }
}

function render() {
  renderSummary(); renderStores(); renderCategories(); renderPages(); renderProductTypes(); renderPolicies(); renderPolicyReview(); renderProducts(); renderPromotions(); renderBookAssets(); renderStaffRoles(); renderDraftHistory(); renderModerationPreview();
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
  const savedDraft = { configuration: clone(config), products: clone(products), promotions: clone(promotions), discountCodes: clone(discountCodes), bookAssetManifest: clone(bookAssetManifest), bookAssetHistory: clone(bookAssetHistory) };
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
    const counts = text('span', `${entry.draft.configuration.catalog.storefronts.length} storefronts · ${entry.draft.configuration.catalog.productCategories.length} categories · ${entry.draft.products.length} products · ${(entry.draft.promotions ?? []).length} discounts · ${(entry.draft.discountCodes ?? []).length} codes · ${(entry.draft.bookAssetManifest ?? []).length} asset records`, 'draft-history-counts'); row.append(counts);
    const restore = text('button', 'Restore this draft', 'button button-soft'); restore.type = 'button';
    restore.addEventListener('click', () => {
      if (!window.confirm(`Restore local draft ${entry.revision}? The current preview draft will be saved in history first.`)) return;
      const current = { configuration: clone(config), products: clone(products), promotions: clone(promotions), discountCodes: clone(discountCodes), bookAssetManifest: clone(bookAssetManifest), bookAssetHistory: clone(bookAssetHistory) };
      draftHistory.unshift({ id: newId('draft-revision'), savedAt: new Date().toISOString(), revision: config.revision, draft: current });
      draftHistory = draftHistory.slice(0, 20);
      config = clone(entry.draft.configuration); products = clone(entry.draft.products); promotions = clone(entry.draft.promotions ?? []); discountCodes = clone(entry.draft.discountCodes ?? []); bookAssetManifest = clone(entry.draft.bookAssetManifest ?? []); bookAssetHistory = clone(entry.draft.bookAssetHistory ?? []);
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ configuration: config, products, promotions, discountCodes, bookAssetManifest, bookAssetHistory }));
      localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(draftHistory));
      localStorage.setItem(BOOK_ASSET_STORAGE_KEY, JSON.stringify(bookAssetManifest));
      localStorage.setItem(BOOK_ASSET_HISTORY_KEY, JSON.stringify(bookAssetHistory));
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
  renderProductCategoryOptions();
}

function renderProductCategoryOptions(selectedIds = null) {
  const typeId = byId('productForm').elements.typeId.value;
  const categorySelect = byId('productForm').elements.categoryIds;
  const selected = new Set(selectedIds ?? [...categorySelect.selectedOptions].map(option => option.value));
  const eligibleStoreIds = new Set(config.catalog.storefronts.filter(store => store.productTypeIds.includes(typeId)).map(store => store.id));
  categorySelect.replaceChildren();
  config.catalog.productCategories.filter(category => category.enabled && eligibleStoreIds.has(category.storefrontId)).forEach(category => {
    const option = document.createElement('option'); option.value = category.id;
    option.textContent = `${category.label.en} · ${storeTitle(category.storefrontId)}`; option.selected = selected.has(category.id); categorySelect.append(option);
  });
}

function toggleBookDetails() {
  const type = config.catalog.productTypes.find(item => item.id === byId('productForm').elements.typeId.value);
  byId('bookDetailsFields').hidden = type?.renderer !== 'book_v1';
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

const BOOK_ASSET_RULES = {
  reader: { maxBytes: 100 * 1024 * 1024, mimeTypes: ['application/pdf'] },
  cover: { maxBytes: 10 * 1024 * 1024, mimeTypes: ['image/jpeg', 'image/png', 'image/webp'] },
  narration: { maxBytes: 100 * 1024 * 1024, mimeTypes: ['audio/mpeg', 'audio/mp4', 'audio/aac', 'audio/ogg', 'audio/wav'] },
  timings: { maxBytes: 5 * 1024 * 1024, mimeTypes: ['application/json', 'text/json'] }
};

async function stageBookAssets(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const bookId = String(data.get('bookId') || '');
  const book = products.find(product => product.id === bookId && product.typeId === 'book');
  const contentVersion = Number(data.get('contentVersion'));
  if (!book || !Number.isSafeInteger(contentVersion) || contentVersion < 1) { window.alert('Choose a book and positive content version.'); return; }
  const rightsHolder = String(data.get('rightsHolder') || '').trim();
  const licenseReference = String(data.get('licenseReference') || '').trim();
  const rightsExpiresAt = String(data.get('rightsExpiresAt') || '') || null;
  const rightsConfirmed = form.elements.distributionRightsConfirmed.checked;
  const timingReviewed = form.elements.timingReviewed.checked;
  if (!rightsHolder || !licenseReference || !rightsConfirmed || (rightsExpiresAt && new Date(`${rightsExpiresAt}T23:59:59`).getTime() < Date.now())) {
    window.alert('Enter the rights holder and license reference, confirm distribution rights, and ensure the license has not expired.'); return;
  }
  const files = Object.keys(BOOK_ASSET_RULES).map(type => [type, form.elements[type].files[0]]).filter(([, file]) => file);
  if (!files.length) { window.alert('Choose at least one asset file to validate.'); return; }
  if (!window.crypto?.subtle) { window.alert('File checksums require HTTPS or localhost in a modern browser.'); return; }
  const locale = String(data.get('locale'));
  const records = [];
  try {
    for (const [type, file] of files) {
      const rule = BOOK_ASSET_RULES[type];
      if (file.size < 1 || file.size > rule.maxBytes) throw new Error(`${file.name} exceeds the ${Math.round(rule.maxBytes / (1024 * 1024))} MiB ${type} size limit.`);
      const acceptedMime = rule.mimeTypes.includes(file.type) || (type === 'timings' && file.name.toLowerCase().endsWith('.json'));
      if (!acceptedMime) throw new Error(`${file.name} has unsupported content type ${file.type || '(unknown)'}.`);
      const bytes = await file.arrayBuffer();
      if (type === 'reader' && new TextDecoder().decode(bytes.slice(0, 5)) !== '%PDF-') throw new Error(`${file.name} does not begin with a PDF signature.`);
      if (type === 'cover' && !validImageSignature(bytes, file.type)) throw new Error(`${file.name} does not match its declared image type.`);
      if (type === 'narration' && !validAudioSignature(bytes, file.type)) throw new Error(`${file.name} does not match a supported audio signature.`);
      if (type === 'timings') validateTimingJson(new TextDecoder().decode(bytes));
      const digest = await window.crypto.subtle.digest('SHA-256', bytes);
      const checksum = [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
      const assetId = `${bookId}-${locale}-v${contentVersion}-${type}`;
      records.push({ assetId, bookId, type, locale, contentVersion, fileName: file.name, mimeType: file.type || 'application/json', byteLength: file.size, sha256: checksum, validatedAt: new Date().toISOString(), uploadState: 'metadata_only', rights: { holder: rightsHolder, licenseReference, expiresAt: rightsExpiresAt, confirmed: rightsConfirmed, timingReviewed } });
    }
    records.forEach(record => {
      const previous = bookAssetManifest.find(existing => existing.assetId === record.assetId) ?? null;
      bookAssetManifest = bookAssetManifest.filter(existing => existing.assetId !== record.assetId);
      bookAssetManifest.unshift(record);
      recordBookAssetHistory(record.assetId, previous ? 'updated' : 'staged', previous, record);
    });
    bookAssetManifest = bookAssetManifest.slice(0, 200);
    localStorage.setItem(BOOK_ASSET_STORAGE_KEY, JSON.stringify(bookAssetManifest));
    localStorage.setItem(BOOK_ASSET_HISTORY_KEY, JSON.stringify(bookAssetHistory));
    form.reset(); form.elements.contentVersion.value = String(contentVersion);
    renderBookAssets();
  } catch (error) { window.alert(error.message); }
}

function validImageSignature(buffer, mimeType) {
  const bytes = new Uint8Array(buffer);
  if (mimeType === 'image/png') return bytes.length >= 8 && [137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => bytes[index] === byte);
  if (mimeType === 'image/jpeg') return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  if (mimeType === 'image/webp') return bytes.length >= 12 && String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP';
  return false;
}

function validAudioSignature(buffer, mimeType) {
  const bytes = new Uint8Array(buffer);
  const header = String.fromCharCode(...bytes.slice(0, 12));
  if (mimeType === 'audio/ogg') return header.startsWith('OggS');
  if (mimeType === 'audio/wav') return header.startsWith('RIFF') && header.slice(8, 12) === 'WAVE';
  if (mimeType === 'audio/mp4') return header.slice(4, 8) === 'ftyp';
  if (mimeType === 'audio/aac') return bytes.length > 1 && bytes[0] === 0xff && (bytes[1] & 0xf6) === 0xf0;
  if (mimeType === 'audio/mpeg') return header.startsWith('ID3') || (bytes.length > 1 && bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0);
  return false;
}

function validateTimingJson(serialized) {
  let document;
  try { document = JSON.parse(serialized); } catch { throw new Error('Word timing file must contain valid JSON.'); }
  if (!Array.isArray(document.words) || !document.words.length) throw new Error('Word timing JSON needs a non-empty words array.');
  let previousStart = -1;
  let previousEnd = -1;
  for (const word of document.words) {
    if (!Number.isFinite(word.startMs) || !Number.isFinite(word.endMs) || word.startMs < 0 || word.endMs <= word.startMs || word.startMs < previousStart || word.startMs < previousEnd) {
      throw new Error('Word timings must have ordered, non-overlapping startMs/endMs values.');
    }
    previousStart = word.startMs; previousEnd = word.endMs;
  }
}

function renderBookAssets() {
  const form = byId('bookAssetForm');
  const selectedBookId = form.elements.bookId.value;
  fillOptions(form.elements.bookId, products.filter(product => product.typeId === 'book' && product.status !== 'archived').map(product => [product.id, `${product.title.en} · ${product.status}`]));
  if (selectedBookId && [...form.elements.bookId.options].some(option => option.value === selectedBookId)) form.elements.bookId.value = selectedBookId;
  const root = byId('bookAssetList'); root.replaceChildren();
  if (!bookAssetManifest.length) root.append(text('div', 'No locally validated asset metadata yet.', 'empty-state'));
  bookAssetManifest.forEach(asset => {
    const row = document.createElement('article'); row.className = 'promotion-row';
    const product = products.find(item => item.id === asset.bookId);
    row.append(text('strong', `${asset.type} · ${product?.title.en ?? asset.bookId} · ${asset.locale.toUpperCase()} v${asset.contentVersion}`));
    row.append(text('span', `${asset.fileName} · ${(asset.byteLength / (1024 * 1024)).toFixed(2)} MiB · SHA-256 ${asset.sha256.slice(0, 16)}… · not uploaded`, 'promotion-meta'));
    row.append(text('span', `Rights: ${asset.rights?.holder ?? 'not recorded'} · ref ${asset.rights?.licenseReference ?? 'missing'} · expiry ${asset.rights?.expiresAt ?? 'not specified'} · ${asset.rights?.confirmed ? 'confirmed by preview user' : 'unconfirmed'}`, 'promotion-meta'));
    const remove = text('button', 'Remove metadata', 'remove-button'); remove.type = 'button'; remove.addEventListener('click', () => { bookAssetManifest = bookAssetManifest.filter(item => item.assetId !== asset.assetId); recordBookAssetHistory(asset.assetId, 'removed', asset, null); localStorage.setItem(BOOK_ASSET_STORAGE_KEY, JSON.stringify(bookAssetManifest)); localStorage.setItem(BOOK_ASSET_HISTORY_KEY, JSON.stringify(bookAssetHistory)); renderBookAssets(); }); row.append(remove);
    root.append(row);
  });
  const history = byId('bookAssetHistory'); history.replaceChildren();
  if (!bookAssetHistory.length) history.append(text('div', 'No local asset edits recorded.', 'empty-state'));
  bookAssetHistory.slice(0, 50).forEach(event => history.append(text('div', `${event.action} · ${event.assetId} · ${event.actor} · ${new Date(event.createdAt).toLocaleString()}`, 'promotion-meta')));
}

function recordBookAssetHistory(assetId, action, before, after) {
  bookAssetHistory.unshift({ assetId, action, actor: 'Local preview user', createdAt: new Date().toISOString(), before: before ? clone(before) : null, after: after ? clone(after) : null });
  bookAssetHistory = bookAssetHistory.slice(0, 200);
}

function updateTargetFields(form, fieldClass) {
  const scope = form.elements.scope.value;
  form.querySelectorAll(`.${fieldClass}`).forEach(field => { field.hidden = field.dataset.scope !== scope; });
}

function populatePromotionTargets() {
  const productSelect = byId('promotionForm').elements.productId;
  const categorySelect = byId('promotionForm').elements.categoryIds;
  const destinationKind = byId('promotionForm').elements.destinationKind.value;
  const destination = byId('promotionForm').elements.destinationId;
  fillOptions(productSelect, products.filter(product => product.status === 'published').map(product => [product.id, `${product.title.en} · ${product.id}`]));
  fillOptions(categorySelect, config.catalog.productCategories.filter(category => category.enabled).map(category => [category.id, `${category.label.en} · ${category.label.bn}`]), true);
  const targets = destinationKind === 'product'
    ? products.filter(product => product.status === 'published').map(product => [product.id, product.title.en])
    : destinationKind === 'category'
      ? config.catalog.productCategories.filter(category => category.enabled).map(category => [category.id, `${category.label.en} · ${category.label.bn}`])
      : config.catalog.storefronts.filter(store => store.enabled).map(store => [store.id, store.title.en]);
  fillOptions(destination, targets);
}

function populateCodeTargets() {
  const form = byId('discountCodeForm');
  fillOptions(form.elements.productId, products.filter(product => product.status === 'published').map(product => [product.id, `${product.title.en} · ${product.id}`]));
  fillOptions(form.elements.categoryIds, config.catalog.productCategories.filter(category => category.enabled).map(category => [category.id, `${category.label.en} · ${category.label.bn}`]), true);
}

function fillOptions(select, entries, preserveSelection = false) {
  const selected = preserveSelection ? new Set([...select.selectedOptions].map(option => option.value)) : new Set([select.value]);
  select.replaceChildren();
  entries.forEach(([value, label]) => { const option = document.createElement('option'); option.value = value; option.textContent = label; option.selected = selected.has(value); select.append(option); });
}

function updateDiscountCodeMode() {
  const unique = byId('discountCodeForm').elements.mode.value === 'unique';
  byId('sharedCodeField').hidden = unique;
  byId('codeCountField').hidden = !unique;
}

function readTargeting(form) {
  const kind = form.elements.scope.value;
  if (kind === 'product') return { kind, productId: form.elements.productId.value };
  if (kind === 'categories') return { kind, categoryIds: [...form.elements.categoryIds.selectedOptions].map(option => option.value) };
  return { kind: 'all' };
}

function readDiscount(form) {
  const type = form.elements.discountType.value;
  const inputValue = Number(form.elements.discountValue.value);
  if (!Number.isFinite(inputValue) || inputValue <= 0) throw new Error('Enter a discount greater than zero.');
  if (type === 'percentage') {
    if (!Number.isInteger(inputValue) || inputValue > 99) throw new Error('Percentage discounts must be whole numbers from 1 to 99.');
    return { type, value: inputValue };
  }
  const value = Math.round(inputValue * 100);
  if (!Number.isSafeInteger(value) || value < 1) throw new Error('Enter a valid fixed amount in BDT.');
  return { type, value, currency: 'BDT' };
}

function readSchedule(form) {
  const zone = form.elements.timeZone.value === 'browser' ? Intl.DateTimeFormat().resolvedOptions().timeZone : form.elements.timeZone.value;
  const starts = zonedDate(form.elements.startsAt.value, zone);
  const ends = zonedDate(form.elements.endsAt.value, zone);
  if (!Number.isFinite(starts.getTime()) || !Number.isFinite(ends.getTime()) || ends <= starts) throw new Error('The end time must be later than the start time.');
  return { startsAt: starts.toISOString(), endsAt: ends.toISOString() };
}

function zonedDate(value, timeZone) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) return new Date(Number.NaN);
  const desired = match.slice(1).map(Number);
  const targetWallTime = Date.UTC(desired[0], desired[1] - 1, desired[2], desired[3], desired[4]);
  let candidate = targetWallTime;
  const formatter = new Intl.DateTimeFormat('en-GB', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const fields = Object.fromEntries(formatter.formatToParts(new Date(candidate)).filter(part => part.type !== 'literal').map(part => [part.type, Number(part.value)]));
    const representedWallTime = Date.UTC(fields.year, fields.month - 1, fields.day, fields.hour, fields.minute);
    candidate += targetWallTime - representedWallTime;
  }
  return new Date(candidate);
}

function selectedDestination(form) {
  const kind = form.elements.destinationKind.value;
  const targetId = form.elements.destinationId.value;
  if (!targetId) throw new Error('Choose a valid banner destination.');
  return { kind, targetId };
}

function savePromotion(event) {
  event.preventDefault();
  const form = event.currentTarget;
  try {
    const name = { en: form.elements.nameEn.value.trim(), bn: form.elements.nameBn.value.trim() };
    const bannerText = { en: form.elements.bannerEn.value.trim(), bn: form.elements.bannerBn.value.trim() };
    const targeting = readTargeting(form);
    if (!name.en || !name.bn || !bannerText.en || !bannerText.bn) throw new Error('Provide English and Bangla campaign and banner text.');
    if (targeting.kind === 'product' && !targeting.productId) throw new Error('Choose a published product.');
    if (targeting.kind === 'categories' && !targeting.categoryIds.length) throw new Error('Choose at least one category.');
    promotions.unshift({
      id: slug(name.en) ? `${slug(name.en)}-${newId('promo').slice(-6)}` : newId('promotion'), status: 'draft', name, targeting,
      discount: readDiscount(form), schedule: readSchedule(form), banner: { text: bannerText, imageAssetId: null, destination: selectedDestination(form) },
      stackingPolicy: 'best_single_discount', priceFloorMinor: 1
    });
    recordPromotionEvent(promotions[0].id, 'created automatic discount draft');
    form.reset(); updateTargetFields(form, 'promotion-target-field'); populatePromotionTargets(); renderPromotions();
  } catch (error) { window.alert(error.message); }
}

async function saveDiscountCode(event) {
  event.preventDefault();
  const form = event.currentTarget;
  try {
    if (!window.crypto?.subtle || !window.crypto?.getRandomValues) throw new Error('Code creation needs a secure browser context (HTTPS or localhost).');
    const mode = form.elements.mode.value;
    const count = mode === 'unique' ? Number(form.elements.count.value) : 1;
    if (!Number.isInteger(count) || count < 1 || count > 100) throw new Error('Generate between 1 and 100 codes at a time.');
    const targeting = readTargeting(form);
    if (targeting.kind === 'product' && !targeting.productId) throw new Error('Choose a published product.');
    if (targeting.kind === 'categories' && !targeting.categoryIds.length) throw new Error('Choose at least one category.');
    const schedule = readSchedule(form);
    const discount = readDiscount(form);
    const eligibility = form.elements.eligibility.value;
    const assignedCustomerIds = String(form.elements.assignedCustomerIds.value || '').split(',').map(value => value.trim()).filter(Boolean);
    if (eligibility === 'assigned_account' && !assignedCustomerIds.length) throw new Error('Provide at least one internal customer account ID.');
    const limitValue = String(form.elements.totalLimit.value || '').trim();
    const totalRedemptionLimit = limitValue ? Number(limitValue) : null;
    const perCustomerLimit = Number(form.elements.perCustomerLimit.value);
    if ((totalRedemptionLimit !== null && (!Number.isSafeInteger(totalRedemptionLimit) || totalRedemptionLimit < 1)) || !Number.isSafeInteger(perCustomerLimit) || perCustomerLimit < 1) throw new Error('Redemption limits must be positive whole numbers.');
    if (firebaseClient?.auth.currentUser) {
      const campaignId = `campaign-${newId('server').slice(-12)}`;
      const result = await callAdmin(firebaseClient, 'createDiscountCodes', {
        campaignId, mode, count, targeting, discount, schedule, eligibility, assignedCustomerIds,
        totalRedemptionLimit, perCustomerLimit
      });
      const serverRecords = result.codes.map(code => ({ id: code.id, campaignId: result.campaignId, codeHash: code.id, mode, status: 'draft', targeting, discount, schedule, eligibility, totalRedemptionLimit: mode === 'unique' ? 1 : totalRedemptionLimit, perCustomerLimit: mode === 'unique' ? 1 : perCustomerLimit, serverManaged: true }));
      discountCodes.unshift(...serverRecords);
      offerOneTimeCodeDownload(result.codes.map(item => item.code), result.campaignId);
      byId('codeCampaignStatus').textContent = 'Created by the trusted service. Download plaintext codes once; only keyed HMAC digests are stored by the server.';
      form.reset(); form.elements.mode.value = mode; form.elements.count.value = '10'; form.elements.perCustomerLimit.value = '1';
      updateDiscountCodeMode(); byId('assignedCustomersField').hidden = true; updateTargetFields(form, 'code-target-field'); populateCodeTargets(); renderPromotions();
      return;
    }
    const rawCodes = mode === 'shared' ? [String(form.elements.code.value || '').trim().toUpperCase()] : Array.from({ length: count }, () => generateUniqueCode());
    if (mode === 'shared' && !/^[A-Z0-9-]{4,24}$/.test(rawCodes[0])) throw new Error('Use 4–24 letters, numbers, or hyphens for a shared code.');
    const campaignId = newId('campaign');
    const now = new Date();
    const records = await Promise.all(rawCodes.map(async (rawCode, index) => ({
      id: newId('code'), campaignId, codeHash: await hashCode(rawCode), mode,
      status: schedule.startsAt > now.toISOString() ? 'scheduled' : 'draft', targeting, discount, schedule, eligibility,
      ...(eligibility === 'assigned_account' ? { assignedCustomerIds } : {}),
      totalRedemptionLimit: mode === 'unique' ? 1 : totalRedemptionLimit,
      perCustomerLimit: mode === 'unique' ? 1 : perCustomerLimit,
      stackingPolicy: 'best_single_discount', priceFloorMinor: 1
    })));
    discountCodes.unshift(...records);
    recordPromotionEvent(campaignId, `created ${records.length} ${mode} discount-code draft${records.length === 1 ? '' : 's'}`);
    offerOneTimeCodeDownload(rawCodes, campaignId);
    form.reset(); form.elements.mode.value = mode; form.elements.count.value = '10'; form.elements.perCustomerLimit.value = mode === 'unique' ? '1' : '1';
    updateDiscountCodeMode(); byId('assignedCustomersField').hidden = true; updateTargetFields(form, 'code-target-field'); populateCodeTargets(); renderPromotions();
  } catch (error) { window.alert(error.message); }
}

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function generateUniqueCode() {
  const random = new Uint8Array(12); window.crypto.getRandomValues(random);
  return `LP-${[...random].map(value => CODE_ALPHABET[value % CODE_ALPHABET.length]).join('')}`;
}

async function hashCode(code) {
  const normalized = code.trim().toUpperCase();
  const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(normalized));
  return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
}

function offerOneTimeCodeDownload(codes, campaignId) {
  const root = byId('oneTimeCodeExport'); root.replaceChildren(); root.hidden = false;
  root.append(text('strong', `${codes.length} code${codes.length === 1 ? '' : 's'} generated. Download them now; plaintext is not saved in browser drafts.`));
  const download = text('button', 'Download codes once', 'button button-primary'); download.type = 'button';
  download.addEventListener('click', () => {
    const csv = `campaign_id,code\n${codes.map(code => `${campaignId},${code}`).join('\n')}\n`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `${campaignId}-codes.csv`; link.click(); URL.revokeObjectURL(url);
    codes.fill(''); download.disabled = true; download.textContent = 'Downloaded; codes cleared from this page';
  }, { once: true });
  root.append(download);
}

function promotionStateLabel(item) {
  const now = Date.now();
  const start = Date.parse(item.schedule.startsAt); const end = Date.parse(item.schedule.endsAt);
  if (item.status === 'active' && now < start) return 'Scheduled';
  if (item.status === 'active' && now >= end) return 'Expired';
  return item.status[0].toUpperCase() + item.status.slice(1);
}

function renderPromotions() {
  populatePromotionTargets(); populateCodeTargets();
  updateTargetFields(byId('promotionForm'), 'promotion-target-field'); updateTargetFields(byId('discountCodeForm'), 'code-target-field');
  const promoList = byId('promotionList'); promoList.replaceChildren();
  if (!promotions.length) promoList.append(text('div', 'No automatic discount drafts yet.', 'empty-state'));
  promotions.forEach(item => promoList.append(renderPromotionRow(item, false)));
  const codeList = byId('discountCodeList'); codeList.replaceChildren();
  const campaigns = new Map();
  discountCodes.forEach(record => { const group = campaigns.get(record.campaignId) ?? []; group.push(record); campaigns.set(record.campaignId, group); });
  if (!campaigns.size) codeList.append(text('div', 'No discount code drafts yet.', 'empty-state'));
  campaigns.forEach(records => codeList.append(renderPromotionRow(records[0], true, records.length)));
  renderPromotionAudit();
}

function recordPromotionEvent(targetId, action) {
  promotionAudit.unshift({ targetId, action, actor: 'Local preview staff', createdAt: new Date().toISOString() });
  promotionAudit = promotionAudit.slice(0, 50);
  localStorage.setItem(PROMOTION_AUDIT_KEY, JSON.stringify(promotionAudit));
  renderPromotionAudit();
}

function renderPromotionAudit() {
  const root = byId('promotionAudit'); if (!root) return;
  root.replaceChildren();
  if (!promotionAudit.length) { root.append(text('div', 'No local promotion actions yet. Production audit events must be recorded by the trusted service.', 'empty-state')); return; }
  promotionAudit.forEach(event => {
    const row = document.createElement('div'); row.className = 'moderation-audit-row';
    row.append(text('strong', event.action)); row.append(text('span', `${event.actor} · ${event.targetId} · ${new Date(event.createdAt).toLocaleString()}`)); root.append(row);
  });
}

function renderPromotionRow(item, isCode, codeCount = 1) {
  const row = document.createElement('article'); row.className = 'promotion-row';
  const discountLabel = item.discount.type === 'percentage' ? `${item.discount.value}% off` : `৳${(item.discount.value / 100).toFixed(2)} off`;
  const targetLabel = item.targeting.kind === 'all' ? 'All eligible products' : item.targeting.kind === 'product'
    ? products.find(product => product.id === item.targeting.productId)?.title.en ?? item.targeting.productId
    : item.targeting.categoryIds.map(id => config.catalog.productCategories.find(category => category.id === id)?.label.en ?? id).join(', ');
  row.append(text('strong', isCode ? `${item.mode === 'unique' ? 'Unique loyalty codes' : 'Shared campaign code'} · ${codeCount} record${codeCount === 1 ? '' : 's'}` : item.name.en));
  row.append(text('span', `${discountLabel} · ${targetLabel} · ${new Date(item.schedule.startsAt).toLocaleString()} – ${new Date(item.schedule.endsAt).toLocaleString()}`, 'promotion-meta'));
  row.append(text('span', promotionStateLabel(item), `status${item.status === 'active' ? '' : ' off'}`));
  const controls = document.createElement('div'); controls.className = 'entity-actions';
  const records = isCode ? discountCodes.filter(record => record.campaignId === item.campaignId) : [item];
  if (isCode && item.serverManaged && firebaseClient?.auth.currentUser) {
    if (!item.approvedAt && item.status === 'draft') controls.append(serverCampaignAction(item, 'Approve', 'approveDiscountCampaign'));
    if (['draft', 'paused'].includes(item.status) && (item.approvedAt || item.status === 'paused')) controls.append(serverCampaignAction(item, 'Publish', 'publishDiscountCampaign'));
    if (['active', 'scheduled'].includes(item.status)) controls.append(serverCampaignAction(item, 'Pause', 'setDiscountCampaignStatus', { status: 'paused' }));
    if (item.status !== 'archived') controls.append(serverCampaignAction(item, 'Archive', 'setDiscountCampaignStatus', { status: 'archived' }));
  } else {
    [['Schedule', 'scheduled'], ['Activate', 'active'], ['Pause', 'paused'], ['Archive', 'archived']].forEach(([label, status]) => {
      const button = text('button', label); button.type = 'button'; button.disabled = records.every(record => record.status === status);
      button.addEventListener('click', () => { records.forEach(record => { record.status = status; }); recordPromotionEvent(item.campaignId ?? item.id, `${label.toLowerCase()} ${isCode ? 'discount code campaign' : 'automatic discount'}`); renderPromotions(); }); controls.append(button);
    });
  }
  row.append(controls); return row;
}

function serverCampaignAction(item, label, callableName, extra = {}) {
  const button = text('button', label, 'button button-soft'); button.type = 'button';
  button.addEventListener('click', async () => {
    const reason = window.prompt(`${label} campaign ${item.campaignId}. Reason:`);
    if (!reason?.trim()) return;
    button.disabled = true;
    try {
      await callAdmin(firebaseClient, callableName, { campaignId: item.campaignId, reason: reason.trim(), ...extra });
      await refreshServerCodeCampaigns();
    } catch (error) { button.disabled = false; byId('codeCampaignStatus').textContent = `${label} failed: ${error.message}`; }
  });
  return button;
}

async function refreshServerCodeCampaigns() {
  if (!firebaseClient?.auth.currentUser) return;
  byId('codeCampaignStatus').textContent = 'Loading private campaign status…';
  try {
    const result = await callAdmin(firebaseClient, 'listDiscountCampaigns', { limit: 100 });
    const currentIds = new Set(result.campaigns.map(campaign => campaign.id));
    discountCodes = discountCodes.filter(record => !record.serverManaged || !currentIds.has(record.campaignId));
    result.campaigns.forEach(campaign => {
      for (let index = 0; index < Math.min(campaign.codeCount ?? 1, 100); index += 1) {
        discountCodes.push({ id: `${campaign.id}-${index}`, campaignId: campaign.id, mode: campaign.mode, status: campaign.status, targeting: campaign.targeting, discount: campaign.discount, schedule: campaign.schedule, eligibility: campaign.eligibility, totalRedemptionLimit: campaign.totalRedemptionLimit, perCustomerLimit: campaign.perCustomerLimit, approvedAt: campaign.approvedAt ?? null, serverManaged: true });
      }
    });
    byId('codeCampaignStatus').textContent = `${result.campaigns.length} private server campaign(s) loaded. Plaintext codes and assigned customer IDs are never fetched.`;
    renderPromotions();
  } catch (error) { byId('codeCampaignStatus').textContent = `Could not load campaigns: ${error.message}`; }
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
  renderProductCategoryOptions(product?.categoryIds ?? []);
  const bookDetails = product?.bookDetails ?? {};
  form.elements.bookAgeBand.value = bookDetails.ageBand ?? '3-5';
  form.elements.readingLevel.value = bookDetails.readingLevel ?? '';
  form.elements.seriesId.value = bookDetails.seriesId ?? '';
  form.elements.seriesOrder.value = bookDetails.seriesOrder ?? '';
  form.elements.pageCount.value = bookDetails.pageCount ?? product?.attributes?.page_count ?? '';
  form.elements.contentVersion.value = bookDetails.contentVersion ?? 1;
  [...form.elements.bookLocales.options].forEach(option => { option.selected = (bookDetails.locales ?? ['en', 'bn']).includes(option.value); });
  form.elements.bookAccess.value = bookDetails.access ?? 'free';
  form.elements.pdfInAppEnabled.checked = bookDetails.pdfInAppEnabled !== false;
  form.elements.physicalEditionAvailable.checked = Boolean(bookDetails.physicalEdition?.available);
  toggleBookDetails();
  renderVariantEditor(product?.variants ?? defaultVariants(form.elements.typeId.value));
  form.elements.physicalVariantId.value = bookDetails.physicalEdition?.variantId ?? '';
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
  if (!type || !titleEn || !titleBn) return;
  let variants;
  try { variants = readVariantRows(); } catch (error) { window.alert(error.message); return; }
  const existing = products.find(item => item.id === productId);
  const id = productId || slug(titleEn) || newId('product');
  if (!productId && products.some(item => item.id === id)) return;
  const categoryIds = data.getAll('categoryIds').map(value => String(value));
  const categories = categoryIds.map(categoryId => config.catalog.productCategories.find(item => item.id === categoryId && item.enabled));
  if (categories.some(category => !category)) return;
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
  let bookDetails;
  if (type.renderer === 'book_v1') {
    const locales = data.getAll('bookLocales').map(value => String(value));
    const pageCount = Number(data.get('pageCount'));
    const contentVersion = Number(data.get('contentVersion'));
    const readingLevel = String(data.get('readingLevel') || '').trim();
    const physicalEditionAvailable = form.elements.physicalEditionAvailable.checked;
    const physicalVariantId = String(data.get('physicalVariantId') || '');
    if (!readingLevel || !Number.isSafeInteger(pageCount) || pageCount < 1 || !Number.isSafeInteger(contentVersion) || contentVersion < 1 || !locales.length) {
      window.alert('Book details need a reading level, page count, content version, and at least one language.'); return;
    }
    if (physicalEditionAvailable && !variants.some(variant => variant.id === physicalVariantId)) {
      window.alert('Select a product variant for the physical edition.'); return;
    }
    const seriesOrderValue = String(data.get('seriesOrder') || '').trim();
    const seriesOrder = seriesOrderValue ? Number(seriesOrderValue) : null;
    if (seriesOrder !== null && (!Number.isSafeInteger(seriesOrder) || seriesOrder < 1)) { window.alert('Series order must be a positive whole number.'); return; }
    bookDetails = {
      ageBand: String(data.get('bookAgeBand')), readingLevel, seriesId: String(data.get('seriesId') || '').trim() || null,
      seriesOrder, pageCount, locales, contentVersion, access: String(data.get('bookAccess')),
      pdfInAppEnabled: true, physicalEdition: { available: physicalEditionAvailable, variantId: physicalEditionAvailable ? physicalVariantId : null }
    };
  }
  const product = {
    ...(existing ?? {}), id, typeId, status: String(data.get('status')),
    title: { en: titleEn, bn: titleBn },
    description: { en: String(data.get('descriptionEn') || '').trim(), bn: String(data.get('descriptionBn') || '').trim() },
    categoryIds, imageAssetIds: existing?.imageAssetIds ?? [], attributes, ...(bookDetails ? { bookDetails } : {}), variants
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

function defaultVariants(typeId) {
  const isBook = config.catalog.productTypes.find(type => type.id === typeId)?.renderer === 'book_v1';
  return [{ id: `${slug(typeId) || 'product'}-standard`, label: { en: isBook ? 'Read in app' : 'Standard', bn: isBook ? 'অ্যাপে পড়ুন' : 'সাধারণ' }, priceMinor: 0, currency: 'BDT', stockState: isBook ? 'not_applicable' : 'out_of_stock', stockQuantity: null }];
}

function renderVariantEditor(variants) {
  const root = byId('variantList'); root.replaceChildren();
  variants.forEach((variant, index) => {
    const card = document.createElement('fieldset'); card.className = 'variant-card';
    const legend = document.createElement('legend'); legend.textContent = `Variant ${index + 1}`; card.append(legend);
    const fields = [
      ['id', 'Variant ID', variant.id ?? ''], ['labelEn', 'Label · English', variant.label?.en ?? ''], ['labelBn', 'Label · বাংলা', variant.label?.bn ?? ''],
      ['price', 'Price · BDT', ((variant.priceMinor ?? 0) / 100).toFixed(2)], ['stockState', 'Availability', variant.stockState ?? 'not_applicable'],
      ['stockQuantity', 'Stock quantity', variant.stockQuantity ?? '']
    ];
    fields.forEach(([key, title, value]) => {
      const label = text('label', title); const input = key === 'stockState' ? document.createElement('select') : document.createElement('input');
      input.dataset.variantField = key;
      if (key === 'price') { input.type = 'number'; input.min = '0'; input.step = '0.01'; }
      if (key === 'stockQuantity') { input.type = 'number'; input.min = '0'; input.step = '1'; }
      if (key === 'stockState') [['not_applicable', 'No stock tracking'], ['in_stock', 'In stock'], ['out_of_stock', 'Out of stock'], ['preorder', 'Preorder']].forEach(([optionValue, optionLabel]) => { const option = document.createElement('option'); option.value = optionValue; option.textContent = optionLabel; input.append(option); });
      input.value = value; label.append(input); card.append(label);
    });
    const remove = text('button', 'Remove variant', 'remove-button'); remove.type = 'button'; remove.disabled = variants.length === 1;
    remove.addEventListener('click', () => { const next = readVariantRows(false); next.splice(index, 1); renderVariantEditor(next); }); card.append(remove); root.append(card);
  });
  const physicalVariant = byId('productForm').elements.physicalVariantId;
  const previousPhysicalVariantId = physicalVariant.value;
  physicalVariant.replaceChildren(new Option('Select a variant', ''));
  variants.forEach(variant => physicalVariant.append(new Option(`${variant.label?.en ?? variant.id} · ${variant.id}`, variant.id)));
  if (variants.some(variant => variant.id === previousPhysicalVariantId)) physicalVariant.value = previousPhysicalVariantId;
  byId('addVariantButton').disabled = variants.length >= 20;
}

function readVariantRows(strict = true) {
  const cards = [...byId('variantList').querySelectorAll('.variant-card')];
  if (!cards.length) throw new Error('Add at least one product variant.');
  const variants = cards.map(card => {
    const value = key => card.querySelector(`[data-variant-field="${key}"]`).value.trim();
    const id = value('id'); const titleEn = value('labelEn'); const titleBn = value('labelBn');
    const priceText = value('price'); const price = Number(priceText); const stockState = value('stockState'); const quantityValue = value('stockQuantity');
    if (strict && (!/^[a-z0-9_-]{2,80}$/.test(id) || !titleEn || !titleBn || !priceText || !Number.isFinite(price) || price < 0 || !Number.isSafeInteger(Math.round(price * 100)) || !['not_applicable', 'in_stock', 'out_of_stock', 'preorder'].includes(stockState))) throw new Error('Each variant needs a valid ID, English/Bangla labels, nonnegative BDT price, and availability.');
    const parsedQuantity = quantityValue ? Number(quantityValue) : null;
    if (strict && parsedQuantity !== null && (!Number.isSafeInteger(parsedQuantity) || parsedQuantity < 0)) throw new Error('Variant stock quantity must be a nonnegative whole number.');
    return { id: id || newId('variant'), label: { en: titleEn || 'Standard', bn: titleBn || 'সাধারণ' }, priceMinor: Number.isFinite(price) && price >= 0 ? Math.round(price * 100) : 0, currency: 'BDT', stockState: stockState || 'not_applicable', stockQuantity: stockState === 'not_applicable' ? null : (stockState === 'in_stock' ? parsedQuantity ?? 0 : parsedQuantity) };
  });
  if (strict && new Set(variants.map(variant => variant.id)).size !== variants.length) throw new Error('Variant IDs must be unique within the product.');
  return variants;
}

const POLICY_KEYS = ['offlinePolicy', 'accessPolicy', 'privacyPolicy', 'physicalCommercePolicy', 'communityPolicy'];

function policySnapshot() {
  return Object.fromEntries(POLICY_KEYS.map(key => [key, clone(config?.[key] ?? {})]));
}

function policyChanges(before, after) {
  const changes = [];
  POLICY_KEYS.forEach(group => {
    const keys = new Set([...Object.keys(before?.[group] ?? {}), ...Object.keys(after?.[group] ?? {})]);
    keys.forEach(key => {
      const oldValue = before?.[group]?.[key]; const newValue = after?.[group]?.[key];
      if (JSON.stringify(oldValue) !== JSON.stringify(newValue)) changes.push({ path: `${group}.${key}`, before: oldValue, after: newValue });
    });
  });
  return changes;
}

function savePolicyReview() { localStorage.setItem(POLICY_REVIEW_KEY, JSON.stringify(policyReview)); }

function recordPolicyEvent(action, details = {}) {
  policyReview.events.unshift({ action, actor: 'Local preview user', createdAt: new Date().toISOString(), ...details });
  policyReview.events = policyReview.events.slice(0, 100);
  savePolicyReview();
}

function previewPolicyImpact() {
  policyReview.impactPreview = policyChanges(policyReview.baseline, policySnapshot());
  policyReview.approval = 'pending';
  recordPolicyEvent('previewed policy impact', { changeCount: policyReview.impactPreview.length });
  renderPolicyReview();
}

function approvePolicyPreview() {
  if (!policyReview.impactPreview) return;
  if (!window.confirm('Record a local preview approval? This does not approve or publish the real policy.')) return;
  policyReview.approval = 'preview_approved';
  recordPolicyEvent('marked local preview approved');
  renderPolicyReview();
}

function savePolicyRevision(note = 'Saved policy preview revision') {
  const policy = policySnapshot();
  if (!policyChanges(policyReview.baseline, policy).length) return;
  policyReview.history.unshift({ id: newId('policy-revision'), revision: (policyReview.history[0]?.revision ?? 0) + 1, createdAt: new Date().toISOString(), note, approval: policyReview.approval, policy });
  policyReview.history = policyReview.history.slice(0, 30);
  policyReview.baseline = clone(policy);
  policyReview.impactPreview = null;
  recordPolicyEvent(note, { revision: policyReview.history[0].revision, approval: policyReview.approval });
  renderPolicyReview();
}

function restorePolicyRevision(entry) {
  if (!window.confirm(`Restore local policy preview revision ${entry.revision}? This only changes the browser draft.`)) return;
  savePolicyRevision('Saved current policy before rollback');
  POLICY_KEYS.forEach(key => { config[key] = clone(entry.policy[key]); });
  policyReview.baseline = policySnapshot();
  policyReview.approval = 'pending';
  policyReview.impactPreview = null;
  recordPolicyEvent('restored local policy preview revision', { revision: entry.revision });
  render();
}

function renderPolicyReview() {
  const status = byId('policyApprovalStatus');
  if (!status) return;
  status.textContent = policyReview.approval === 'preview_approved' ? 'Local preview marked approved · real owner approval is still required' : 'Pending owner review';
  byId('approvePolicyPreviewButton').disabled = !policyReview.impactPreview || policyReview.approval === 'preview_approved';
  const impact = byId('policyImpactPreview'); impact.replaceChildren();
  const changes = policyReview.impactPreview ?? policyChanges(policyReview.baseline, policySnapshot());
  if (!policyReview.impactPreview) impact.append(text('div', 'Preview policy impact to compare the current draft with its saved baseline.', 'empty-state'));
  else if (!changes.length) impact.append(text('div', 'No policy changes from the saved baseline.', 'empty-state'));
  else changes.forEach(change => impact.append(text('div', `${change.path}: ${JSON.stringify(change.before)} → ${JSON.stringify(change.after)}`, 'policy-audit-row')));
  const history = byId('policyRevisionHistory'); history.replaceChildren();
  if (!policyReview.history.length) history.append(text('div', 'No local policy revisions yet.', 'empty-state'));
  policyReview.history.forEach(entry => {
    const row = document.createElement('div'); row.className = 'policy-audit-row';
    row.append(text('span', `Revision ${entry.revision} · ${entry.note} · ${new Date(entry.createdAt).toLocaleString()} · ${entry.approval === 'preview_approved' ? 'preview approved' : 'pending owner review'}`));
    const restore = text('button', 'Restore preview', 'button button-soft'); restore.type = 'button'; restore.addEventListener('click', () => restorePolicyRevision(entry));
    row.append(restore); history.append(row);
  });
  const events = byId('policyAuditHistory'); events.replaceChildren();
  if (!policyReview.events.length) events.append(text('div', 'No local policy actions yet.', 'empty-state'));
  policyReview.events.slice(0, 30).forEach(event => events.append(text('div', `${event.action} · ${event.actor} · ${new Date(event.createdAt).toLocaleString()}`, 'policy-audit-row')));
}

function policyEdited() {
  policyReview.approval = 'pending';
  policyReview.impactPreview = null;
  savePolicyReview();
  renderPolicyReview();
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
      { key: 'deletionRetentionDays', label: 'Retention period (days, blank = undecided)', kind: 'nullableNumber', min: 0 },
      { key: 'requiresOwnerAndLegalReview', label: 'Owner/legal review still required', kind: 'boolean' }
    ] },
    { key: 'physicalCommercePolicy', title: 'Physical commerce', fields: [
      { key: 'enabled', label: 'Enable physical orders', kind: 'boolean' },
      { key: 'availableRegions', label: 'Regions (comma separated)', kind: 'regions' },
      { key: 'paymentMethods', label: 'Payment methods', kind: 'methods' },
      { key: 'showInAppPdfAndPhysicalOptionsWhenBothAvailable', label: 'Offer both book formats when available', kind: 'boolean' },
      { key: 'requireConfirmedStockAndDeliveryQuoteBeforeCheckout', label: 'Confirm stock and delivery before checkout', kind: 'boolean' },
      { key: 'cancellationAndRefundRules', label: 'Cancellation/refund summary', kind: 'nullableText' },
      { key: 'requiresOwnerReview', label: 'Owner review still required', kind: 'boolean' }
    ] },
    { key: 'communityPolicy', title: 'Parent community', fields: [
      { key: 'parentAccountRequired', label: 'Require parent account', kind: 'boolean' },
      { key: 'moderateBeforePublicVisibility', label: 'Review posts before public visibility', kind: 'boolean' },
      { key: 'prohibitChildContactAndLocationDetails', label: 'Block child contact/location details', kind: 'boolean' },
      { key: 'maximumImageBytes', label: 'Image size limit (MB)', kind: 'megabytes' },
      { key: 'maximumImagesPerPost', label: 'Images per post', kind: 'number', min: 0 },
      { key: 'allowUserCreatedGroups', label: 'Allow parent-created groups', kind: 'boolean' },
      { key: 'reportAndBlockEnabled', label: 'Enable report and block', kind: 'boolean' },
      { key: 'moderationResponseTargetHours', label: 'Response target (hours, blank = undecided)', kind: 'nullableNumber', min: 0 },
      { key: 'requiresOwnerReview', label: 'Owner review still required', kind: 'boolean' }
    ] }
  ];
  groups.forEach(group => {
    const panel = document.createElement('section'); panel.className = 'policy-card';
    panel.append(node('h3', group.title));
    group.fields.forEach(field => {
      const label = node('label', field.label, `policy-field${field.kind === 'boolean' ? ' boolean-field' : ''}`);
      if (field.kind === 'boolean') {
        const input = document.createElement('input'); input.type = 'checkbox'; input.checked = Boolean(config[group.key][field.key]);
        input.addEventListener('change', () => { config[group.key][field.key] = input.checked; policyEdited(); }); label.append(input);
      } else if (field.kind === 'select') {
        const input = document.createElement('select'); field.options.forEach(([value, title]) => { const option = document.createElement('option'); option.value = value; option.textContent = title; input.append(option); });
        input.value = config[group.key][field.key]; input.addEventListener('change', () => { config[group.key][field.key] = input.value; policyEdited(); }); label.append(input);
      } else if (field.kind === 'methods') {
        const wrap = document.createElement('span'); wrap.className = 'method-options';
        [['bkash', 'bKash'], ['visa', 'Visa']].forEach(([value, title]) => {
          const optionLabel = node('label', title, 'method-option'); const input = document.createElement('input'); input.type = 'checkbox'; input.checked = config[group.key][field.key].includes(value);
          input.addEventListener('change', () => { config[group.key][field.key] = [...new Set([...config[group.key][field.key].filter(item => item !== value), ...(input.checked ? [value] : [])])]; policyEdited(); }); optionLabel.prepend(input); wrap.append(optionLabel);
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
          policyEdited();
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
    product.categoryIds.forEach(id => {
      const category = categories.find(item => item.id === id);
      if (!category) errors.push(`${product.title.en} references a missing category.`);
      else if (!stores.some(store => store.id === category.storefrontId && store.productTypeIds.includes(product.typeId))) errors.push(`${product.title.en} uses category ${category.id} from an incompatible storefront.`);
    });
    const productType = types.find(type => type.id === product.typeId);
    if (productType?.renderer === 'book_v1') {
      const details = product.bookDetails;
      if (!details || !['0-2', '3-5', '6-8', '9-12'].includes(details.ageBand) || !details.readingLevel?.trim() || !Number.isInteger(details.pageCount) || details.pageCount < 1 || !Number.isInteger(details.contentVersion) || details.contentVersion < 1 || !['free', 'premium', 'paid'].includes(details.access) || details.pdfInAppEnabled !== true || !Array.isArray(details.locales) || !details.locales.length || details.locales.some(locale => !['en', 'bn'].includes(locale))) errors.push(`${product.title.en} needs valid book-specific reading and access details.`);
      if (product.status === 'published' && details?.locales?.length) {
        details.locales.forEach(locale => {
          const assets = bookAssetManifest.filter(asset => asset.bookId === product.id && asset.locale === locale && asset.contentVersion === details.contentVersion);
          ['reader', 'cover'].forEach(requiredType => {
            const asset = assets.find(item => item.type === requiredType);
            if (!asset) errors.push(`${product.title.en} cannot publish ${locale.toUpperCase()} v${details.contentVersion} without a validated ${requiredType} asset record.`);
          });
          assets.forEach(asset => {
            const rights = asset.rights;
            const rightsCurrent = rights?.confirmed && rights.holder?.trim() && rights.licenseReference?.trim() && (!rights.expiresAt || Date.parse(`${rights.expiresAt}T23:59:59`) >= Date.now());
            if (!rightsCurrent) errors.push(`${product.title.en} ${asset.type} ${locale.toUpperCase()} needs confirmed, current distribution rights metadata.`);
            if (!/^[a-f0-9]{64}$/.test(asset.sha256 ?? '') || !Number.isSafeInteger(asset.byteLength) || asset.byteLength < 1) errors.push(`${product.title.en} ${asset.type} ${locale.toUpperCase()} has invalid file integrity metadata.`);
          });
          const narration = assets.find(item => item.type === 'narration');
          const timings = assets.find(item => item.type === 'timings');
          if (Boolean(narration) !== Boolean(timings)) errors.push(`${product.title.en} ${locale.toUpperCase()} narration and word timings must be staged together.`);
          if (narration && (!narration.rights?.timingReviewed || !timings?.rights?.timingReviewed)) errors.push(`${product.title.en} ${locale.toUpperCase()} narration timing needs a script-review confirmation.`);
        });
      }
    }
    (productType?.attributes ?? []).forEach(definition => {
      const value = product.attributes?.[definition.id];
      if (definition.required && (value === undefined || value === null || value === '' || (typeof value === 'string' && !value.trim()))) errors.push(`${product.title.en} needs ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'number' && (typeof value !== 'number' || !Number.isFinite(value))) errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'boolean' && typeof value !== 'boolean') errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'enum' && !(definition.options ?? []).some(option => option.value === value)) errors.push(`${product.title.en} has an unsupported ${definition.label.en}.`);
      if (value !== undefined && definition.valueType === 'text_list' && !Array.isArray(value)) errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
      if (value !== undefined && ['text', 'enum'].includes(definition.valueType) && typeof value !== 'string') errors.push(`${product.title.en} has an invalid ${definition.label.en}.`);
    });
    if (!product.variants.length) errors.push(`${product.title.en} needs at least one variant.`);
    unique(product.variants, `Variants on ${product.id}`);
    product.variants.forEach(variant => {
      localized(variant.label, `Variant ${variant.id}`);
      if (!/^[a-z0-9_-]{2,80}$/.test(variant.id ?? '') || !Number.isSafeInteger(variant.priceMinor) || variant.priceMinor < 0 || variant.currency !== 'BDT' || !['in_stock', 'out_of_stock', 'preorder', 'not_applicable'].includes(variant.stockState) || (variant.stockQuantity != null && (!Number.isInteger(variant.stockQuantity) || variant.stockQuantity < 0))) errors.push(`${product.title.en} has an invalid BDT variant or stock value.`);
    });
  });
  const validateOffer = (offer, label) => {
    if (!offer.id || !offer.schedule || !Number.isFinite(Date.parse(offer.schedule.startsAt)) || !Number.isFinite(Date.parse(offer.schedule.endsAt)) || Date.parse(offer.schedule.endsAt) <= Date.parse(offer.schedule.startsAt)) errors.push(`${label} needs a valid start and later end time.`);
    if (offer.targeting?.kind === 'product' && !productsById.has(offer.targeting.productId)) errors.push(`${label} targets a missing product.`);
    if (offer.targeting?.kind === 'categories' && (!offer.targeting.categoryIds?.length || offer.targeting.categoryIds.some(id => !categories.some(category => category.id === id && category.enabled)))) errors.push(`${label} needs enabled categories that exist.`);
    if (!['all', 'product', 'categories'].includes(offer.targeting?.kind)) errors.push(`${label} has an invalid target.`);
    if (!offer.discount || !['percentage', 'fixed_amount'].includes(offer.discount.type) || !Number.isInteger(offer.discount.value) || offer.discount.value < 1 || (offer.discount.type === 'percentage' && offer.discount.value > 99)) errors.push(`${label} has an invalid discount; prices cannot become free.`);
    if (offer.discount?.type === 'fixed_amount' && offer.discount.currency !== 'BDT') errors.push(`${label} must use BDT.`);
  };
  const offerIds = [...promotions.map(item => item.id), ...discountCodes.map(item => item.id)];
  if (new Set(offerIds).size !== offerIds.length) errors.push('Promotion and code record IDs must be unique.');
  promotions.forEach((promotion, index) => {
    const label = `Promotion ${promotion.name?.en || index + 1}`;
    validateOffer(promotion, label);
    localized(promotion.name, label); localized(promotion.banner?.text, `${label} banner`);
    const destination = promotion.banner?.destination;
    const validDestination = destination?.kind === 'product' ? productsById.has(destination.targetId)
      : destination?.kind === 'category' ? categories.some(category => category.id === destination.targetId)
        : destination?.kind === 'storefront' ? stores.some(store => store.id === destination.targetId) : false;
    if (!validDestination) errors.push(`${label} banner destination is invalid.`);
  });
  discountCodes.forEach(record => {
    validateOffer(record, `Discount code ${record.id}`);
    if (!/^[a-f0-9]{64}$/.test(record.codeHash ?? '')) errors.push(`Discount code ${record.id} needs a SHA-256 code hash.`);
    if (!['any', 'new_customer', 'assigned_account'].includes(record.eligibility)) errors.push(`Discount code ${record.id} has invalid customer eligibility.`);
    if (!Number.isInteger(record.perCustomerLimit) || record.perCustomerLimit < 1 || (record.totalRedemptionLimit != null && (!Number.isInteger(record.totalRedemptionLimit) || record.totalRedemptionLimit < 1))) errors.push(`Discount code ${record.id} has invalid redemption limits.`);
  });
  unique(bookAssetManifest, 'Book asset');
  bookAssetManifest.forEach(asset => {
    if (!productsById.has(asset.bookId) || !/^[a-f0-9]{64}$/.test(asset.sha256 ?? '') || !Number.isInteger(asset.byteLength) || asset.byteLength < 1 || asset.uploadState !== 'metadata_only') errors.push(`Book asset ${asset.assetId} has invalid local validation metadata.`);
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
  const blob = new Blob([JSON.stringify({ draftSchemaVersion: 1, baseConfigurationRevision: config.revision, configuration: config, products, promotions, discountCodes, bookAssetManifest, bookAssetHistory }, null, 2)], { type: 'application/json' });
  const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `littlepages-draft-revision-${config.revision ?? 1}.json`; link.click();
  URL.revokeObjectURL(link.href);
}

function exportPublicationPayload() {
  const errors = validateDraft();
  const revisionValue = byId('basePublicationRevisionInput').value.trim();
  const basePublicationRevision = revisionValue === '' ? Number.NaN : Number(revisionValue);
  if (errors.length || !Number.isSafeInteger(basePublicationRevision) || basePublicationRevision < 0) {
    renderValidation();
    if (revisionValue === '' || !Number.isSafeInteger(basePublicationRevision) || basePublicationRevision < 0) {
      const item = text('li', 'Enter a valid current publication revision before exporting the Functions payload.', 'validation-error');
      byId('validationList').append(item);
    }
    return;
  }
  const payload = {
    basePublicationRevision,
    snapshot: {
      configuration: clone(config),
      products: clone(products),
      promotions: clone(promotions)
    },
    minimumClientVersions: {}
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `littlepages-functions-draft-base-${basePublicationRevision}.json`; link.click();
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
