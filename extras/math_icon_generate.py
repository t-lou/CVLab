## this program can be useful for generating an icon for mathematical function
## here for Gaussian
import cv2
import numpy
import math

size = (500, 500);
width = 5;
height = 350;


def func(x):
    return math.exp(-x * x / 0.3);

img = numpy.zeros([size[0], size[1], 4], numpy.uint8);

y = [func(float(x - size[1] / 2) / float(size[1] / 2)) for x in range(size[1])];
y = numpy.array(y);
scale = float(height) / (y.max() - y.min());
offset = (size[0] - height) / 2;
minimal = y.min();
y = [int(scale * (i - minimal) + offset) for i in y];

# x axis
for i in range(size[1]):
    for j in range(1 - width, width):
        if(offset + j >= 0 and offset + j < size[0]):
            img[size[0] - offset - j, i] = numpy.array([53, 53, 53, 255], numpy.uint8);

# y axis
for i in range(size[0]):
    for j in range(1 - width, width):
        img[i, j + size[1] / 2] = numpy.array([53, 53, 53, 255], numpy.uint8);

# plot
for i in range(size[1]):
    value = y[i];
    for j0 in range(1 - width, width):
        for j1 in range(1 - width, width):
            if(value + j0 >= 0 and value + j0 < size[0] and i + j1 >= 0 and i + j1 < size[1]
                    and j0 * j0 + j1 * j1 < width * width):
                img[size[0] - value - j0, i + j1] = numpy.array([0, 0, 153, 255], numpy.uint8);


cv2.imwrite("icon.png", img);
