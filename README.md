# Zxing-Custom
Proyecto de Android que modifica la aplicación de Zxing para poder usarla como una librería de Android

Se ha cogido el código fuente de la aplicación Barcode Scanner de este repositorio con Licencia Apache https://github.com/zxing/zxing. 

Se coge el código fuente de la carpeta android. La parte core y core-android de Zxing está integradas en el proyecto como dependencias de Gradle.

Se elimina del código fuente todo lo que no es referente al escaneo de códigos con la cámara. Una vez limpio, se comprueba que el código de BarcodeScanner está bastante acoplado en torno a `CaptureActivity`, casi todas las clases importantes para el escaneo como `CaptureActivityHandler`, `DecodeThread` o `DecodeHandler` reciben en su constructor a `CaptureActivity`. 

Como no queremos utilizar `CaptureActivity` directamente al usar la biblioteca, se decide hacer una interfaz llamada `CaptureActivityInterface` que exponga todos los métodos que utilizan las clases que dependen de `CaptureActivity` y se cambian sus constructores para que reciban y usen un `CaptureActivityInterface` en vez de un `CaptureActivity`.

Así, el código que quiera utilizar esta biblioteca, puede crear una `Activity` que tiene que inicializar la librera e implementar `CaptureActivityInterface`.

## Instalación

En la pestaña de releases de Github están las versiones con un enlace de descarga directamente al aar ya compilado.

Si se quiere generar un aar directamente, se puede bajar el código, importarlo en Android Studio, ejecutar build y utilizar el aar que genera.

Una vez tengamos el arr, hay que copiarlo a la carpeta de libs del proyecto destino y enlazarlo con Gradle mediante un comando como este añadiendo esta línea a la sección `dependencies` en Gradle.

`compile (name:'zxing_3.2.0', ext:'aar')`

## Ejemplo de cómo usar la biblioteca

```

public class ScanActivity extends BaseActivity implements CaptureActivityInterface, SurfaceHolder.Callback {

    @Bind(R.id.surfaceViewCamera) SurfaceView surfaceView;
    @Bind(R.id.containerCameraPreview) View viewContainerCamera;
    @Bind(R.id.headerScan) View header;
    @Bind(R.id.btnCantScan) Button btnCantScan;

    private Collection<BarcodeFormat> supportedBarcodeFormats;

    private CameraManager cameraManager;
    private CaptureActivityHandler captureActivityHandler;
    private boolean hasSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCameraPreview();
    }

    private void init() {
        hasSurface = false;
        cameraManager = new CameraManager(getApplication(), (int) getResources().getDimension(R.dimen.height_blue_header_gradient));
        supportedBarcodeFormats = Collections.singletonList(BarcodeFormat.QR_CODE);
    }

    private void startCameraPreview() {
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCameraPreview(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
        }
    }

    private void initCameraPreview(SurfaceHolder surfaceHolder) {
        if (cameraManager.isOpen()) {
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            if (captureActivityHandler == null) {
                captureActivityHandler = new CaptureActivityHandler(this, supportedBarcodeFormats, null, null, cameraManager);
            }
        } catch (Exception ignored) {

        }
    }

    private void stopCameraPreview() {
        if (captureActivityHandler != null) {
            captureActivityHandler.quitSynchronously();
            captureActivityHandler = null;
        }
        cameraManager.closeDriver();
    }

    @Override
    public ViewfinderView getViewfinderView() {
        return null;
    }

    @Override
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        Logger.log("camera", "decode " + rawResult.getText());
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public Handler getHandler() {
        return captureActivityHandler;
    }

    @Override
    public void drawViewfinder() {

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (!hasSurface) {
            hasSurface = true;
            initCameraPreview(surfaceHolder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Point cameraResolution = cameraManager.getCameraResolution();
        double realHeight = width * cameraResolution.x / cameraResolution.y;
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) viewContainerCamera.getLayoutParams();
        params.width = width;
        params.height = (int) realHeight;
        int heightFooter = btnCantScan.getHeight();
        if (realHeight < (height - heightFooter)) {
            int topMargin = (int) (height - realHeight - heightFooter);
            if (topMargin > header.getHeight()) {
                RelativeLayout.LayoutParams paramFooter = (RelativeLayout.LayoutParams) btnCantScan.getLayoutParams();
                paramFooter.height = paramFooter.height + (topMargin - header.getHeight());
                topMargin = header.getHeight();
            }
            params.topMargin = topMargin;
        } else {
            cameraManager.setRectCameraPreview(getRectFromView(viewContainerCamera));
        }
    }

    private Rect getRectFromView(View v) {
        int[] pointA = new int[2];
        v.getLocationOnScreen(pointA);
        Rect rectA = new Rect(pointA[0], pointA[1], pointA[0] + v.getWidth(), pointA[1] + v.getHeight());
        return rectA;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        hasSurface = false;
    }
}

```
