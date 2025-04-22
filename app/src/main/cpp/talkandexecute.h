#ifndef _TALKANDEXECUTE_H_
#define _TALKANDEXECUTE_H_

#include <string>
#include <vector>

class talkandexecute {
public:
    talkandexecute() {};
    ~talkandexecute() {};
    std::vector<float> transcribeFileWithMel(const char *waveFile, std::vector<float> filters);
    std::vector<float> returnMelSpectrogram(std::vector<float> samples, std::vector<float> filters);

private:
    // Add any private members or helper functions.
};

#endif // _TALKANDEXECUTE_H_

