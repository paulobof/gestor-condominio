import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  createParkingRental,
  getParkingRental,
  updateParkingRental,
} from '../api/parkingRentalsApi';

function backendMessage(e: unknown, fallback: string): string {
  return (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}

export function ParkingRentalFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [tower, setTower] = useState('');
  const [floor, setFloor] = useState('');
  const [spotNumber, setSpotNumber] = useState('');
  const [price, setPrice] = useState('');

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    getParkingRental(id)
      .then((r) => {
        if (!active) return;
        setTower(r.tower);
        setFloor(r.floor);
        setSpotNumber(r.spotNumber);
        setPrice(String(r.monthlyPrice));
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar o anúncio.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id]);

  const parsedPrice = (): number | null => {
    const raw = price.trim();
    if (!raw) return null;
    // pt-BR: se há vírgula decimal, os pontos são separadores de milhar (ex.: "1.350,00").
    // Sem vírgula, Number() lida com "350" e "350.00" diretamente.
    const normalized = raw.includes(',') ? raw.replace(/\./g, '').replace(',', '.') : raw;
    const n = Number(normalized);
    return Number.isFinite(n) && n > 0 ? n : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!tower.trim() || !floor.trim() || !spotNumber.trim()) {
      toast.error('Preencha torre, andar e numeração.');
      return;
    }
    const monthlyPrice = parsedPrice();
    if (monthlyPrice == null) {
      toast.error('Informe um valor mensal maior que zero.');
      return;
    }
    setSaving(true);
    try {
      const body = {
        tower: tower.trim(),
        floor: floor.trim(),
        spotNumber: spotNumber.trim(),
        monthlyPrice,
      };
      if (isEdit && id) {
        await updateParkingRental(id, body);
        toast.success('Anúncio atualizado.');
        navigate(`/vagas/aluguel/${id}`);
      } else {
        const created = await createParkingRental(body);
        toast.success('Vaga anunciada.');
        navigate(`/vagas/aluguel/${created.id}`, { replace: true });
      }
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao salvar o anúncio.'));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-2xl p-4">Carregando…</main>;

  return (
    <main className="mx-auto max-w-2xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to={isEdit && id ? `/vagas/aluguel/${id}` : '/vagas/aluguel'}>
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'Editar anúncio' : 'Anunciar vaga'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="tower">Torre</Label>
              <Input
                id="tower"
                value={tower}
                onChange={(e) => setTower(e.target.value)}
                maxLength={40}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="floor">Andar</Label>
              <Input
                id="floor"
                placeholder="Ex.: -1, Térreo, 2"
                value={floor}
                onChange={(e) => setFloor(e.target.value)}
                maxLength={20}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="spotNumber">Numeração da vaga</Label>
              <Input
                id="spotNumber"
                value={spotNumber}
                onChange={(e) => setSpotNumber(e.target.value)}
                maxLength={40}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="price">Valor mensal em R$</Label>
              <Input
                id="price"
                type="text"
                inputMode="decimal"
                placeholder="Ex.: 350,00"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Anunciar vaga'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
