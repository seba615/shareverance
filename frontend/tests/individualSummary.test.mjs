import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
const server = await createServer({ configFile: false, server: { middlewareMode: true, watch: null }, appType: 'custom' });
after(() => server.close());
const { IndividualSummaryResults, IndividualSummaryPanel } = await server.ssrLoadModule('/src/IndividualSummaryPanel.tsx');
const render = data => renderToStaticMarkup(createElement(IndividualSummaryResults, { data }));
test('resumen renderiza participación exacta y categoría histórica inactiva', () => {
  const markup = render({totalAmount:25.01,expenseCount:2,fromDate:'2024-01-01',toDate:'2024-01-31',categories:[
    {categoryId:1,categoryName:'Actual',categoryActive:true,amount:5.01,expenseCount:1},
    {categoryId:2,categoryName:'Histórica',categoryActive:false,amount:20,expenseCount:1}]});
  assert.match(markup,/25,01/); assert.match(markup,/Histórica \(inactiva\)/);
  assert.match(markup,/2024-01-01/); assert.match(markup,/Mi participación/);
});
test('sin partes informa vacío y cero, sin inferir cobertura insuficiente', () => {
  const markup = render({totalAmount:0,expenseCount:0,fromDate:null,toDate:null,categories:[]});
  assert.match(markup,/No tenés participación/); assert.match(markup,/0,00/);
  assert.doesNotMatch(markup,/insuficiente|deuda pendiente/);
});

const members = [{id:1,userName:'Admin',isActive:true},{id:2,userName:'Ana',isActive:false}];
test('ADMIN dispone del selector de miembros, incluidos históricos inactivos', () => {
  const markup = renderToStaticMarkup(createElement(IndividualSummaryPanel,{organizationId:1,refreshToken:0,members,isAdmin:true,currentMembershipId:1}));
  assert.match(markup, /<select/); assert.match(markup, /Ana \(inactivo\)/);
});
test('USER no dispone del selector de otros miembros', () => {
  const markup = renderToStaticMarkup(createElement(IndividualSummaryPanel,{organizationId:1,refreshToken:0,members,isAdmin:false,currentMembershipId:2}));
  assert.doesNotMatch(markup, /<select|Ana/);
});
test('el resumen de otro miembro no atribuye su participación al usuario conectado', () => {
  const markup = renderToStaticMarkup(createElement(IndividualSummaryResults,{data:{totalAmount:75,expenseCount:3,categories:[]},own:false}));
  assert.match(markup, /Participación total/); assert.doesNotMatch(markup, /Mi participación|No tenés/);
});
