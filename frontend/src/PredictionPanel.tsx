import { FormEvent, useEffect, useState } from 'react';
import { ApiError, Category, predictionApi, PredictionResponse } from './api/java';
import {
  createPredictionLoader,
  formatMinor,
  formatPredictionMethod,
  formatPredictionMonth,
  validatePredictionTargetMonth,
} from './predictionModel';

interface Props {
  organizationId: number;
  categories: Category[];
  refreshToken: number;
}

export function PredictionPanel({ organizationId, categories, refreshToken }: Props) {
  const [targetMonthInput, setTargetMonthInput] = useState('');
  const [appliedTargetMonth, setAppliedTargetMonth] = useState('');
  const [validationError, setValidationError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<{
    key: string;
    data: PredictionResponse | null;
    error: string;
  } | null>(null);

  const [loader] = useState(() =>
    createPredictionLoader(predictionApi.monthly, (key, data, error) => {
      const message =
        error instanceof ApiError && error.status === 401
          ? 'La sesión venció. Iniciá sesión nuevamente.'
          : error instanceof ApiError && error.status === 403
          ? 'No tenés permisos para consultar las predicciones de esta organización.'
          : error instanceof ApiError && error.status === 503
          ? 'El servicio de predicciones no está disponible en este momento. Intentá nuevamente más tarde.'
          : error
          ? 'No se pudo consultar la predicción. Intentá nuevamente.'
          : '';
      setResult({ key, data, error: message });
    })
  );

  const key = JSON.stringify([organizationId, appliedTargetMonth, refreshToken, refresh]);
  const active = result?.key === key ? result : null;
  const data = active?.data;

  useEffect(() => {
    void loader.load(key, organizationId, appliedTargetMonth || undefined);
    return () => loader.invalidate();
  }, [key, loader, organizationId, appliedTargetMonth]);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const error = validatePredictionTargetMonth(targetMonthInput);
    setValidationError(error);
    if (!error) {
      setAppliedTargetMonth(targetMonthInput.trim());
      setRefresh(value => value + 1);
    }
  }

  function handleResetToAuto() {
    setTargetMonthInput('');
    setAppliedTargetMonth('');
    setValidationError('');
    setRefresh(value => value + 1);
  }

  const categoryLabel = (categoryId: number) => {
    const category = categories.find(c => c.id === categoryId);
    return category ? `${category.name}${category.isActive ? '' : ' (inactiva)'}` : `Categoría #${categoryId}`;
  };

  const isInsufficient = data?.organization.status === 'INSUFFICIENT_HISTORY';

  return (
    <section className="prediction-panel" aria-label="Sección de predicciones">
      <h3>Predicción de gastos de la organización</h3>
      <p className="muted">
        Estimación mensual proyectada en ARS calculada a partir del historial continuo de meses con cobertura confirmada.
      </p>

      {data?.simulated && (
        <div className="prediction-badge simulated" role="note">
          <strong>Cálculo simulado (mock):</strong> valores sintéticos para verificación de integración; no constituyen una estimación económica real.
        </div>
      )}

      {data && !data.simulated && (
        <div className="prediction-badge experimental" role="note">
          <strong>Modelo experimental:</strong> Suavizado Exponencial Simple (SES) versión {data.organization.model_version}.
        </div>
      )}

      <form className="prediction-filters" onSubmit={handleSubmit}>
        <label>
          Mes objetivo (opcional)
          <input
            type="month"
            min="0001-01"
            max="9999-12"
            value={targetMonthInput}
            onChange={event => {
              setTargetMonthInput(event.target.value);
              setValidationError('');
            }}
            placeholder="Mes recomendado"
          />
        </label>
        <button className="primary-button" type="submit">
          Consultar
        </button>
        {(appliedTargetMonth || targetMonthInput) && (
          <button className="secondary-button" type="button" onClick={handleResetToAuto}>
            Mes recomendado
          </button>
        )}
        <button className="secondary-button" type="button" onClick={() => setRefresh(value => value + 1)}>
          Actualizar
        </button>
      </form>

      {validationError && (
        <p className="prediction-error" role="alert">
          {validationError}
        </p>
      )}

      {!active && <p className="muted" role="status">Calculando predicción…</p>}

      {active?.error && (
        <p className="prediction-error" role="alert">
          {active.error}
        </p>
      )}

      {data && (
        <>
          <div className="prediction-target-header">
            <p className="eyebrow">Mes proyectado</p>
            <h4>
              {formatPredictionMonth(data.target_month)}{' '}
              <span className="muted">({data.target_month})</span>
            </h4>
          </div>

          {isInsufficient ? (
            <div className="prediction-card insufficient" role="alert">
              <h5>Historial insuficiente para calcular la predicción</h5>
              <p>
                Se requieren al menos <strong>{data.organization.required_months} meses continuos</strong> con cobertura confirmada inmediatamente anteriores a{' '}
                {formatPredictionMonth(data.target_month)}. Actualmente la serie continua cuenta con{' '}
                <strong>{data.organization.months_used}</strong> de los {data.organization.required_months} meses necesarios.
              </p>
              {data.organization.months_used === 0 && (
                <p className="muted">
                  No se registran meses confirmados continuos anteriores al mes objetivo. Un administrador puede confirmar la cobertura de meses cerrados en la sección de analítica.
                </p>
              )}
              <div className="prediction-amount-block disabled">
                <span className="amount-label">Importe organizacional proyectado:</span>
                <span className="amount-value">No disponible</span>
              </div>
            </div>
          ) : (
            <div className="prediction-card success">
              <div className="prediction-amount-block">
                <span className="amount-label">Gasto organizacional proyectado:</span>
                <span className="amount-value">
                  {formatMinor(data.organization.estimated_amount_minor)}
                </span>
              </div>
              <div className="prediction-meta-grid">
                <div>
                  <span className="meta-label">Historial utilizado</span>
                  <strong>
                    {data.organization.months_used} de {data.organization.required_months} meses requeridos
                  </strong>
                </div>
                <div>
                  <span className="meta-label">Método aplicado</span>
                  <strong>
                    {formatPredictionMethod(data.organization.method, data.simulated)}
                  </strong>
                </div>
                <div>
                  <span className="meta-label">Versión del modelo</span>
                  <strong>v{data.organization.model_version}</strong>
                </div>
                <div>
                  <span className="meta-label">Estado</span>
                  <span className="badge badge-success">Disponible</span>
                </div>
              </div>
            </div>
          )}

          <div className="prediction-categories-section">
            <h5>Desglose por categoría</h5>
            {data.categories.length === 0 ? (
              <p className="muted">No se registran gastos en las categorías para el período analizado.</p>
            ) : (
              <div className="prediction-table-scroll">
                <table>
                  <caption>Desglose predictivo por categoría para {data.target_month}</caption>
                  <thead>
                    <tr>
                      <th scope="col">Categoría</th>
                      <th scope="col">Estado</th>
                      <th scope="col">Estimación proyectada</th>
                      <th scope="col">Historial</th>
                      <th scope="col">Método</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.categories.map(cat => {
                      const isCatInsufficient = cat.status === 'INSUFFICIENT_HISTORY';
                      return (
                        <tr key={cat.category_id}>
                          <td>{categoryLabel(cat.category_id)}</td>
                          <td>
                            {isCatInsufficient ? (
                              <span className="badge badge-warning">
                                Insuficiente ({cat.months_used}/{cat.required_months} m)
                              </span>
                            ) : (
                              <span className="badge badge-success">Disponible</span>
                            )}
                          </td>
                          <td>
                            {isCatInsufficient
                              ? 'No disponible'
                              : formatMinor(cat.estimated_amount_minor)}
                          </td>
                          <td>
                            {cat.months_used} / {cat.required_months} meses
                          </td>
                          <td>{formatPredictionMethod(cat.method, data.simulated)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  );
}
