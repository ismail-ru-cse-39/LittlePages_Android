const SETTINGS_KEY = 'littlepages-admin-firebase-settings-v1';
let activeClient = null;

export async function connectFirebaseAdmin() {
  if (activeClient) return activeClient;
  const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) || 'null');
  if (!saved?.config || !saved?.siteKey) return null;
  const appApi = await import('https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js');
  const authApi = await import('https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js');
  const appCheckApi = await import('https://www.gstatic.com/firebasejs/11.10.0/firebase-app-check.js');
  const firestoreApi = await import('https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js');
  const functionsApi = await import('https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js');
  const app = appApi.getApps().length ? appApi.getApp() : appApi.initializeApp(saved.config);
  const auth = authApi.getAuth(app);
  const appCheck = appCheckApi.initializeAppCheck(app, {
    provider: new appCheckApi.ReCaptchaV3Provider(saved.siteKey),
    isTokenAutoRefreshEnabled: true
  });
  const db = firestoreApi.getFirestore(app);
  const functions = functionsApi.getFunctions(app, 'asia-south1');
  activeClient = { app, auth, appCheck, db, functions, authApi, firestoreApi, functionsApi };
  return activeClient;
}

export function saveFirebaseAdminSettings(config, siteKey) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify({ config, siteKey }));
}

export async function readCurrentPublication(client) {
  const ref = client.firestoreApi.doc(client.db, 'publicConfiguration', 'current');
  const snapshot = await client.firestoreApi.getDoc(ref);
  if (!snapshot.exists()) return { publicationRevision: 0 };
  const manifest = snapshot.data();
  if (!Number.isSafeInteger(manifest.publicationRevision) || manifest.publicationRevision < 1) {
    throw new Error('The current publication manifest is invalid.');
  }
  return manifest;
}

export async function callAdmin(client, name, data = {}) {
  const callable = client.functionsApi.httpsCallable(client.functions, name);
  const result = await callable(data);
  return result.data;
}

export async function signInAdmin(client, email, password) {
  return client.authApi.signInWithEmailAndPassword(client.auth, email, password);
}

export async function signOutAdmin(client) {
  await client.authApi.signOut(client.auth);
}

export function watchAdminAuth(client, callback) {
  return client.authApi.onAuthStateChanged(client.auth, callback);
}
