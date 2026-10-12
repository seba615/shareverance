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