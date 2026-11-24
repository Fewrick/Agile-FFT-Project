import numpy as np

def fft(x: list[float]) -> tuple[list[float], list[float]]:
    """Compute the forward FFT."""
    X = np.fft.fft(x)
    return X.real.tolist(), X.imag.tolist()

def ifft(XRe: list[float], XIm: list[float]) -> list[float]:
    """Compute the inverse FFT and return the real samples."""
    X = np.array(XRe) + 1j * np.array(XIm)
    x = np.fft.ifft(X)
    return x.real.tolist()