from abc import ABC, abstractmethod
from collections.abc import Sequence


class Predictor(ABC):
    """Contrato interno compartido por los predictores."""

    @property
    @abstractmethod
    def method(self) -> str:
        pass

    @property
    @abstractmethod
    def model_version(self) -> str:
        pass

    @property
    @abstractmethod
    def required_months(self) -> int:
        pass

    @property
    @abstractmethod
    def simulated(self) -> bool:
        pass

    @abstractmethod
    def predict(self, amounts: Sequence[int]) -> int:
        """
        Estima el próximo importe en centavos.

        Recibe una serie cronológica validada, no negativa,
        con al menos required_months elementos.

        Devuelve un entero no negativo dentro del límite
        monetario del contrato.
        """
        pass

    def forecast(self, amounts: Sequence[int], horizon: int) -> list[int]:
        """Compatibilidad de los modelos existentes; nunca agrega observaciones futuras."""
        if type(horizon) is not int or not 1 <= horizon <= 6:
            raise ValueError("El horizonte debe estar entre 1 y 6.")
        history = list(amounts)
        estimates = []
        for _ in range(horizon):
            estimates.append(self.predict(history))
            history.append(estimates[-1])
        return estimates
