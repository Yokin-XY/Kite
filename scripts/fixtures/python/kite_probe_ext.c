#define PY_SSIZE_T_CLEAN
#include <Python.h>

static PyObject *kite_probe_answer(PyObject *self, PyObject *args) {
    (void) self;
    (void) args;
    return PyLong_FromLong(42);
}

static PyObject *kite_probe_runtime(PyObject *self, PyObject *args) {
    (void) self;
    (void) args;
    return PyUnicode_FromString("kite-glibc-host");
}

static PyMethodDef kite_probe_methods[] = {
    {"answer", kite_probe_answer, METH_NOARGS, "Return the fixed ABI probe value."},
    {"runtime", kite_probe_runtime, METH_NOARGS, "Return the fixed runtime probe identity."},
    {NULL, NULL, 0, NULL},
};

static struct PyModuleDef kite_probe_module = {
    PyModuleDef_HEAD_INIT,
    "kite_probe_ext",
    "Kite reproducible CPython ABI probe.",
    -1,
    kite_probe_methods,
};

PyMODINIT_FUNC PyInit_kite_probe_ext(void) {
    return PyModule_Create(&kite_probe_module);
}
