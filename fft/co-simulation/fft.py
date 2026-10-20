import numpy as np

def sine_input(samples: int, cycles: float, amplitude: float, integer_input: bool = False) -> np.ndarray:
    """Generate a sine wave with an optional integer representation."""
    n = np.arange(samples)
    x = amplitude * np.sin(2 * np.pi * cycles * n / samples)
    if integer_input:
        x = np.rint(x).astype(np.int64)
    return x

def fft(x: list[float]) -> tuple[list[float], list[float]]:
    """Compute the forward FFT."""
    X = np.fft.fft(x)
    return X.real.tolist(), X.imag.tolist()

def ifft(XRe: list[float], XIm: list[float]) -> list[float]:
    """Compute the inverse FFT and return the real samples."""
    X = np.array(XRe) + 1j * np.array(XIm)
    x = np.fft.ifft(X)
    return x.real.tolist()