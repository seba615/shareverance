import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

// Vite transforma los TSX reales del producto sin añadir dependencias de pruebas.
const server = await createServer({ configFile: false, server: { middlewareMode: true, watch: null }, appType: 'custom' });
after(() => server.close());
const { PredictionHistory, PredictionInsufficiencyNotice, PredictionModelNotice } =
  await server.ssrLoadModule('/src/PredictionMessages.tsx');
const render = (component, props) => renderToStaticMarkup(createElement(component, props));

test('insuficiencia renderizada no interpreta months_used=0 como cero meses disponibles', () => {
  // El backend devuelve este resultado aun con tres meses confirmados.
  const result = { status: 'INSUFFICIENT_HISTORY', estimated_amount_minor: null, months_used: 0,
    required_months: 6, method: 'mock', model_version: '1' };
  const notice = render(PredictionInsufficiencyNotice, { requiredMonths: result.required_months, targetMonth: '2026-04' });
  const history = render(PredictionHistory, { result });
  assert.match(notice, /6 meses continuos/);
  assert.match(notice, /no informa cuántos meses están disponibles/);
  assert.match(notice, /No disponible/);
  assert.doesNotMatch(notice, /No se registran meses|sección de analítica|0 de 6/);
  assert.match(history, /No utilizado; mínimo 6 meses requeridos/);
  assert.doesNotMatch(history, /0/);
});

test('resultado OK muestra meses efectivamente utilizados, sin confundirlos con cobertura disponible', () => {
  assert.match(render(PredictionHistory, { result: { status: 'OK', months_used: 12, required_months: 6 } }), /12 meses utilizados \(mínimo 6\)/);
});

test('simulación se identifica sin mostrar versión ni detalles de aceptación', () => {
  const markup = render(PredictionModelNotice, { simulated: true });
  assert.match(markup, /Simulación/);
  assert.match(markup, /último importe del historial consultado/);
  assert.doesNotMatch(markup, /versión|experimental|calidad|aceptad/);
});

test('los modelos reales no muestran avisos técnicos de calidad al usuario', () => {
  assert.equal(render(PredictionModelNotice, { simulated: false }), '');
});
