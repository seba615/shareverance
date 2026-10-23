import type { PredictionResult } from './api/java';
import { formatPredictionMethod, formatPredictionMonth } from './predictionModel';

export function PredictionModelNotice({ simulated, method, version }: {
  simulated: boolean; method: string; version: string;
}) {
  return simulated ? (
    <div className="prediction-badge simulated" role="note">
      <strong>Cálculo simulado (mock), versión {version}:</strong> el método de prueba reutiliza el último importe del historial consultado cuando hay historial suficiente. No constituye una predicción validada.
    </div>
  ) : (
    <div className="prediction-badge experimental" role="note">
      <strong>Modelo experimental:</strong> {formatPredictionMethod(method, false)}, versión {version}. Su calidad predictiva todavía no está aceptada.
    </div>
  );
}

export function PredictionInsufficiencyNotice({ requiredMonths, targetMonth }: {
  requiredMonths: number; targetMonth: string;
}) {
  return <>
    <h5>Historial insuficiente para calcular la predicción</h5>
    <p>Se requieren al menos <strong>{requiredMonths} meses continuos</strong> con cobertura confirmada inmediatamente anteriores a {formatPredictionMonth(targetMonth)}.</p>
    <p className="muted">No se ejecutó el modelo por historial insuficiente. Esta respuesta no informa cuántos meses están disponibles; no significa que no existan meses confirmados. Revisá la cobertura de los meses cerrados con un administrador.</p>
    <div className="prediction-amount-block disabled">
      <span className="amount-label">Importe organizacional proyectado:</span>
      <span className="amount-value">No disponible</span>
    </div>
  </>;
}

export function PredictionHistory({ result }: { result: PredictionResult }) {
  return result.status === 'INSUFFICIENT_HISTORY'
    ? <span>No utilizado; mínimo {result.required_months} meses requeridos</span>
    : <span>{result.months_used} meses utilizados (mínimo {result.required_months})</span>;
}
