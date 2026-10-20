import { FormEvent, useEffect, useState } from 'react';
import { ApiError, Category, predictionApi, PredictionResponse } from './api/java';
import { PredictionHistory, PredictionInsufficiencyNotice, PredictionModelNotice } from './PredictionMessages';
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

      {data && <PredictionModelNotice simulated={data.simulated} method={data.organization.method}
        version={data.organization.model_version} />}

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
              <PredictionInsufficiencyNotice requiredMonths={data.organization.required_months} targetMonth={data.target_month} />
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
                    <PredictionHistory result={data.organization} />
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
                                Historial insuficiente
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
                            <PredictionHistory result={cat} />
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
