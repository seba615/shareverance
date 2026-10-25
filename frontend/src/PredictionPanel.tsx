import { FormEvent, useEffect, useState } from 'react';
import { ApiError, Category, predictionApi, PredictionResponse, ForecastResponse } from './api/java';
import { PredictionHistory, PredictionInsufficiencyNotice, PredictionModelNotice } from './PredictionMessages';
import {
  createPredictionLoader,
  formatMinor,
  formatPredictionMonth,
  validatePredictionTargetMonth,
  validatePredictionOriginMonth,
} from './predictionModel';

interface Props {
  organizationId: number;
  categories: Category[];
  refreshToken: number;
}

export function PredictionPanel({ organizationId, categories, refreshToken }: Props) {
  const [mode, setMode] = useState<'monthly' | 'forecast'>('monthly');

  // Modo mensual
  const [targetMonthInput, setTargetMonthInput] = useState('');
  const [appliedTargetMonth, setAppliedTargetMonth] = useState('');
  const [monthlyValidationError, setMonthlyValidationError] = useState('');
  const [monthlyRefresh, setMonthlyRefresh] = useState(0);
  const [monthlyResult, setMonthlyResult] = useState<{
    key: string;
    data: PredictionResponse | null;
    error: string;
  } | null>(null);

  // Modo multihorizonte
  const [originMonthInput, setOriginMonthInput] = useState('');
  const [appliedOriginMonth, setAppliedOriginMonth] = useState('');
  const [horizonInput, setHorizonInput] = useState<4 | 5 | 6>(4);
  const [appliedHorizon, setAppliedHorizon] = useState<4 | 5 | 6>(4);
  const [forecastValidationError, setForecastValidationError] = useState('');
  const [forecastRefresh, setForecastRefresh] = useState(0);
  const [forecastResult, setForecastResult] = useState<{
    key: string;
    data: ForecastResponse | null;
    error: string;
  } | null>(null);

  const [monthlyLoader] = useState(() =>
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
      setMonthlyResult({ key, data, error: message });
    })
  );

  const [forecastLoader] = useState(() =>
    createPredictionLoader(
      predictionApi.forecast,
      (key, data, error) => {
        const message =
          error instanceof ApiError && error.status === 401
            ? 'La sesión venció. Iniciá sesión nuevamente.'
            : error instanceof ApiError && error.status === 403
            ? 'No tenés permisos para consultar las previsiones de esta organización.'
            : error instanceof ApiError && error.status === 503
            ? 'El servicio de predicciones no está disponible en este momento. Intentá nuevamente más tarde.'
            : error
            ? 'No se pudo consultar la previsión multihorizonte. Intentá nuevamente.'
            : '';
        setForecastResult({ key, data, error: message });
      }
    )
  );

  const monthlyKey = JSON.stringify([organizationId, appliedTargetMonth, refreshToken, monthlyRefresh]);
  const activeMonthly = monthlyResult?.key === monthlyKey ? monthlyResult : null;
  const monthlyData = activeMonthly?.data;

  const forecastKey = JSON.stringify([organizationId, appliedOriginMonth, appliedHorizon, refreshToken, forecastRefresh]);
  const activeForecast = forecastResult?.key === forecastKey ? forecastResult : null;
  const forecastData = activeForecast?.data;

  useEffect(() => {
    if (mode === 'monthly') {
      void monthlyLoader.load(monthlyKey, organizationId, appliedTargetMonth || undefined);
    }
    return () => monthlyLoader.invalidate();
  }, [mode, monthlyKey, monthlyLoader, organizationId, appliedTargetMonth]);

  useEffect(() => {
    if (mode === 'forecast') {
      void forecastLoader.load(forecastKey, organizationId, appliedOriginMonth || undefined, appliedHorizon);
    }
    return () => forecastLoader.invalidate();
  }, [mode, forecastKey, forecastLoader, organizationId, appliedOriginMonth, appliedHorizon]);

  function handleMonthlySubmit(event: FormEvent) {
    event.preventDefault();
    const error = validatePredictionTargetMonth(targetMonthInput);
    setMonthlyValidationError(error);
    if (!error) {
      setAppliedTargetMonth(targetMonthInput.trim());
      setMonthlyRefresh(value => value + 1);
    }
  }

  function handleMonthlyResetToAuto() {
    setTargetMonthInput('');
    setAppliedTargetMonth('');
    setMonthlyValidationError('');
    setMonthlyRefresh(value => value + 1);
  }

  function handleForecastSubmit(event: FormEvent) {
    event.preventDefault();
    const error = validatePredictionOriginMonth(originMonthInput);
    setForecastValidationError(error);
    if (!error) {
      setAppliedOriginMonth(originMonthInput.trim());
      setAppliedHorizon(horizonInput);
      setForecastRefresh(value => value + 1);
    }
  }

  function handleForecastResetToAuto() {
    setOriginMonthInput('');
    setAppliedOriginMonth('');
    setForecastValidationError('');
    setForecastRefresh(value => value + 1);
  }

  const categoryLabel = (categoryId: number) => {
    const category = categories.find(c => c.id === categoryId);
    return category ? `${category.name}${category.isActive ? '' : ' (inactiva)'}` : `Categoría #${categoryId}`;
  };

  const isMonthlyInsufficient = monthlyData?.organization.status === 'INSUFFICIENT_HISTORY';
  const isForecastInsufficient = forecastData?.organization.status === 'INSUFFICIENT_HISTORY';

  return (
    <section className="prediction-panel" aria-label="Sección de predicciones">
      <h3>Proyección de gastos</h3>
      <p className="muted">
        Estimación basada en tus gastos anteriores. Los importes pueden variar. Valores en ARS.
      </p>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
        <button
          type="button"
          className={mode === 'monthly' ? 'primary-button' : 'secondary-button'}
          onClick={() => setMode('monthly')}
        >
          Próximo mes (1 mes)
        </button>
        <button
          type="button"
          className={mode === 'forecast' ? 'primary-button' : 'secondary-button'}
          onClick={() => setMode('forecast')}
        >
          Previsión multihorizonte (4 a 6 meses)
        </button>
      </div>

      {mode === 'monthly' && (
        <>
          {monthlyData && (
            <PredictionModelNotice
              simulated={monthlyData.simulated}
            />
          )}

          <form className="prediction-filters" onSubmit={handleMonthlySubmit}>
            <label>
              Mes objetivo (opcional)
              <input
                type="month"
                min="0001-01"
                max="9999-12"
                value={targetMonthInput}
                onChange={event => {
                  setTargetMonthInput(event.target.value);
                  setMonthlyValidationError('');
                }}
                placeholder="Mes recomendado"
              />
            </label>
            <button className="primary-button" type="submit">
              Consultar
            </button>
            {(appliedTargetMonth || targetMonthInput) && (
              <button className="secondary-button" type="button" onClick={handleMonthlyResetToAuto}>
                Mes recomendado
              </button>
            )}
            <button className="secondary-button" type="button" onClick={() => setMonthlyRefresh(value => value + 1)}>
              Actualizar
            </button>
          </form>

          {monthlyValidationError && (
            <p className="prediction-error" role="alert">
              {monthlyValidationError}
            </p>
          )}

          {!activeMonthly && <p className="muted" role="status">Calculando predicción…</p>}

          {activeMonthly?.error && (
            <p className="prediction-error" role="alert">
              {activeMonthly.error}
            </p>
          )}

          {monthlyData && (
            <>
              <div className="prediction-target-header">
                <p className="eyebrow">Mes proyectado</p>
                <h4>
                  {formatPredictionMonth(monthlyData.target_month)}{' '}
                  <span className="muted">({monthlyData.target_month})</span>
                </h4>
              </div>

              {isMonthlyInsufficient ? (
                <div className="prediction-card insufficient" role="alert">
                  <PredictionInsufficiencyNotice
                    requiredMonths={monthlyData.organization.required_months}
                    targetMonth={monthlyData.target_month}
                  />
                </div>
              ) : (
                <div className="prediction-card success">
                  <div className="prediction-amount-block">
                    <span className="amount-label">Gasto organizacional proyectado:</span>
                    <span className="amount-value">
                      {formatMinor(monthlyData.organization.estimated_amount_minor)}
                    </span>
                  </div>
                  <div className="prediction-meta-grid">
                    <div>
                      <span className="meta-label">Historial utilizado</span>
                      <strong>
                        <PredictionHistory result={monthlyData.organization} />
                      </strong>
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
                {monthlyData.categories.length === 0 ? (
                  <p className="muted">No se registran gastos en las categorías para el período analizado.</p>
                ) : (
                  <div className="prediction-table-scroll">
                    <table>
                      <caption>Desglose predictivo por categoría para {monthlyData.target_month}</caption>
                      <thead>
                        <tr>
                          <th scope="col">Categoría</th>
                          <th scope="col">Estado</th>
                          <th scope="col">Estimación proyectada</th>
                          <th scope="col">Historial</th>
                        </tr>
                      </thead>
                      <tbody>
                        {monthlyData.categories.map(cat => {
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
        </>
      )}

      {mode === 'forecast' && (
        <>
          <form className="prediction-filters" onSubmit={handleForecastSubmit}>
            <label>
              Mes base / origen (opcional)
              <input
                type="month"
                min="0001-01"
                max="9999-12"
                value={originMonthInput}
                onChange={event => {
                  setOriginMonthInput(event.target.value);
                  setForecastValidationError('');
                }}
                placeholder="Último mes confirmado"
              />
            </label>
            <label>
              Horizonte de proyección
              <select
                value={horizonInput}
                onChange={e => setHorizonInput(Number(e.target.value) as 4 | 5 | 6)}
              >
                <option value={4}>4 meses</option>
                <option value={5}>5 meses</option>
                <option value={6}>6 meses</option>
              </select>
            </label>
            <button className="primary-button" type="submit">
              Consultar
            </button>
            {(appliedOriginMonth || originMonthInput) && (
              <button className="secondary-button" type="button" onClick={handleForecastResetToAuto}>
                Último mes confirmado
              </button>
            )}
            <button className="secondary-button" type="button" onClick={() => setForecastRefresh(value => value + 1)}>
              Actualizar
            </button>
          </form>

          {forecastValidationError && (
            <p className="prediction-error" role="alert">
              {forecastValidationError}
            </p>
          )}

          {!activeForecast && <p className="muted" role="status">Calculando previsión multihorizonte…</p>}

          {activeForecast?.error && (
            <p className="prediction-error" role="alert">
              {activeForecast.error}
            </p>
          )}

          {forecastData && (
            <>
              <div className="prediction-target-header">
                <p className="eyebrow">Mes base de origen y horizonte proyectado</p>
                <h4>
                  Origen: {formatPredictionMonth(forecastData.origin_month)}{' '}
                  <span className="muted">({forecastData.origin_month})</span> — Horizonte: {forecastData.horizon_months} meses
                </h4>
              </div>

              <PredictionModelNotice simulated={forecastData.simulated} />

              {isForecastInsufficient ? (
                <div className="prediction-card insufficient" role="alert">
                  <h5>Historial insuficiente para calcular la previsión multihorizonte</h5>
                  <p>
                    Se requieren al menos <strong>{forecastData.organization.required_months} meses continuos</strong> con cobertura confirmada inmediatamente anteriores o iguales a {formatPredictionMonth(forecastData.origin_month)}.
                  </p>
                </div>
              ) : (
                <div className="prediction-card success">
                  <div className="prediction-amount-block">
                    <span className="amount-label">Proyección organizacional ({forecastData.horizon_months} meses):</span>
                  </div>
                  <div className="prediction-table-scroll" style={{ marginTop: '0.75rem' }}>
                    <table>
                      <caption>Proyección multimes organizacional desde {forecastData.origin_month}</caption>
                      <thead>
                        <tr>
                          <th scope="col">Mes proyectado</th>
                          <th scope="col">Gasto proyectado</th>
                        </tr>
                      </thead>
                      <tbody>
                        {forecastData.organization.forecasts.map(pt => (
                          <tr key={pt.horizon}>
                            <td><strong>{formatPredictionMonth(pt.target_month)}</strong> ({pt.target_month})</td>
                            <td>{formatMinor(pt.estimated_amount_minor)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <div className="prediction-meta-grid" style={{ marginTop: '1rem' }}>
                    <div>
                      <span className="meta-label">Historial utilizado</span>
                      <strong>{forecastData.organization.months_used} meses</strong>
                    </div>
                    <div>
                      <span className="meta-label">Estado</span>
                      <span className="badge badge-success">Disponible</span>
                    </div>
                  </div>
                </div>
              )}

              <div className="prediction-categories-section">
                <h5>Desglose por categoría a lo largo del horizonte</h5>
                {forecastData.categories.length === 0 ? (
                  <p className="muted">No se registran gastos en las categorías para el período analizado.</p>
                ) : (
                  <div className="prediction-table-scroll">
                    <table>
                      <caption>Desglose predictivo por categoría y horizonte desde {forecastData.origin_month}</caption>
                      <thead>
                        <tr>
                          <th scope="col">Categoría</th>
                          <th scope="col">Estado</th>
                          <th scope="col">Historial</th>
                          {forecastData.organization.forecasts.map(p => (
                            <th key={p.horizon} scope="col">
                              {formatPredictionMonth(p.target_month)}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {forecastData.categories.map(cat => {
                          const isCatInsufficient = cat.status === 'INSUFFICIENT_HISTORY';
                          return (
                            <tr key={cat.category_id}>
                              <td>{categoryLabel(cat.category_id)}</td>
                              <td>
                                {isCatInsufficient ? (
                                  <span className="badge badge-warning">Historial insuficiente</span>
                                ) : (
                                  <span className="badge badge-success">Disponible</span>
                                )}
                              </td>
                              <td>
                                {isCatInsufficient
                                  ? `Mín. ${cat.required_months} m.`
                                  : `${cat.months_used} m.`}
                              </td>
                              {isCatInsufficient ? (
                                <td colSpan={forecastData.horizon_months} className="muted">
                                  No disponible por historial insuficiente
                                </td>
                              ) : (
                                cat.forecasts.map(pt => (
                                  <td key={pt.horizon}>
                                    {formatMinor(pt.estimated_amount_minor)}
                                  </td>
                                ))
                              )}
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
        </>
      )}
    </section>
  );
}
