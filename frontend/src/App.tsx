import { useState } from 'react';
type Status = { java: string; database: string; demoRows: number; python: string };
export default function App() {
  const [result, setResult] = useState<Status | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function check() {
    setBusy(true); setError(''); setResult(null);
    try {
      const response = await fetch('/api/dev/status');
      if (!response.ok) throw new Error(`Java respondió HTTP ${response.status}`);
      setResult(await response.json());
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo conectar'); }
    finally { setBusy(false); }
  }
  return <main>
    <p>Shareverance · Desarrollo</p><h1>Comprobar el entorno</h1>
    <p>Esta pantalla verifica la comunicación entre servicios. Las funciones de negocio se desarrollarán después.</p>
    <button onClick={check} disabled={busy}>{busy ? 'Comprobando…' : 'Verificar conexiones'}</button>
    {error && <p role="alert">{error}</p>}
    {result && <dl>{Object.entries(result).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>}
  </main>;
}
