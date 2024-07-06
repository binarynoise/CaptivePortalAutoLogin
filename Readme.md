# Captive Portal Auto Login

> The only way to deal with an unfree world is to become so absolutely free that your very existence is an act of rebellion.
>
> — Albert Camus

## TODO

- [ ] Android app
  - [ ] UI
    - [ ] -> settings screen
  - [ ] Background service
  - [ ] notify Android system about success:
    - Shizuku
    - https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Connectivity/framework/src/android/net/CaptivePortal.java;l=151;drc=34a9878cfab528b7973f35665ddf16a7d2a942c0;bpv=1;bpt=1
    - 
  - [ ] Toasts -> Notification updates
  - [ ] proper permission handling
  - [ ] implement export and upload of hars
- [ ] Android light version
  - [ ] only service and permissions
- [ ] Linux service
  - [ ] NetworkManager-integration
  - [ ] others ? 
- [ ] Windows service?
- [ ] frontend common
  - [ ] exponential backoff
  - [ ] collect metrics
    - [ ] portal url
    - [ ] ssid
    - [ ] exception (failure only)
    - [ ] http logs (failure only)
    - [ ] location?
  - [ ] better icon?
- [ ] backend
  - [ ] collect metrics
- [ ] proper logging backend
- [ ] more quotes
