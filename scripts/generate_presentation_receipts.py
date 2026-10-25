"""Generador de comprobantes exclusivamente sintéticos; usa Pillow declarado por OCR."""
import argparse
from datetime import date
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


def generate(assets):
    config=json.loads((assets/'config.json').read_text(encoding='utf-8'))
    day=date.fromisoformat(config['receipt_date']).strftime('%d/%m/%Y')
    image=Image.new('RGB',(1100,900),'white')
    draw=ImageDraw.Draw(image)
    font=ImageFont.load_default(size=48)
    lines=['COMPROBANTE DE PRUEBA','DEMO SHAREVERANCE',f'Fecha: {day}',
           'Compra de prueba','TOTAL: 1234,56','SIN VALIDEZ FISCAL']
    for index,text in enumerate(lines):
        draw.text((65,65+index*120),text,font=font,fill='black')
    image.save(assets/'comprobante-sintetico.png')
    Image.new('RGB',(800,600),'white').save(assets/'comprobante-sin-texto.png')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets',type=Path,required=True)
    generate(parser.parse_args().assets)
