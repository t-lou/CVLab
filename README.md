# CVLab

Hey guys, want to use your Android phone fullier? Maybe we can make a software together! 

This app aims at building an environment for computer vision. Starting with a simple camera, I want to build a tool which can handle a lot of stuff, from image enhancement with filters and thresholding to more amazing things like SLAM, shape analysis and object recognation. I think this should soon be possible with powerful computation of GPU and rapid development of smartphone.

If you are interested and bring skills about Android and/or Computer Vision, feel free to contact me tongxi.lou@tum.de. I am a student at Technical University of Munich and a starter at Android Development. It is totally possible that this App would be useful in the future and play a role in academy or industrie. 

Special update: because ComputeShader is only available for opengles 3.1 or higher, I have decided to use "RenderScript" for high performance computing(my new phone Huawei P8 Lite supports only opengles 2.0, though the OS is already 5.0.1). According to online document, this module should call GPU and CPU parallely and automatically, hopefully it would make complicated computation run in real-time.

IDE: Android Studio
Minimal SDK: Android 5.0, API level 21(5.1 or higher is recommanded, because in Android 5.0 there is a bug about U and V channels of camera)

|    | Accomplished |
| ------------- | ------------- |
| 1  | basic camera  |
| 2  | file browser  |
| 3  | some filters |
| 4  | gui for image processing  |
| 5  | batch processing(but overflow in ImageReader happens)  |
| 6  | live mode with real-time batch and different resolution |

|    | Next |
| ------------- | ------------- |
| 1  | feature detection  |
| 2  | segmentation |
| 3  | camera calibration  |
| 4  | 3D reconstruction |
| 5  | tuning of resolution  |
| 6  | optimization |
