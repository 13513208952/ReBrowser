(() => {
'use strict';
if(window.top!==window || location.origin!=='https://music.163.com' || location.pathname!=='/st/webplayer')return;
if(window.__rbMusicLayoutHostV1)return;
if(typeof CSS==='undefined'||!CSS.supports('display','grid')||!CSS.supports('selector(:has(*))'))return;
let portrait=__INITIAL_PORTRAIT__, timer=0, attempts=0, disposed=false;
const install=()=>{
  // Start closed without animating from the original desktop sidebar; later drawer animation is unchanged.
  const side=document.querySelector('.Container_c1udi5wp');
  const transition=side.style.getPropertyValue('transition'),priority=side.style.getPropertyPriority('transition');
  side.style.setProperty('transition','none','important');
  try{(() => {
'use strict';
if (location.origin !== 'https://music.163.com' || location.pathname !== '/st/webplayer') return {ok:false,reason:'wrong-page'};

window.__rbNeteasePortraitLayoutV1?.cleanup();
const body=document.body,side=document.querySelector('.Container_c1udi5wp'),main=document.querySelector('.RightContainer_r8p6pvv');
if(!side||!main)return {ok:false,reason:'unsupported-layout'};
const oldInert=side.inert;
let open=false,disposed=false,lastRoute='';
const nodes=[],bindings=[],marked=new Set(),coverStyles=new Map();
const style=document.createElement('style');style.id='rb-music-mobile-style';
style.textContent=`
html.rb-music-mobile,html.rb-music-mobile body{width:100%;height:100%;overflow:hidden!important;}
.rb-music-mobile .AppEle_a1llnfj4{width:100%!important;min-width:0!important;height:100dvh!important;overflow:hidden;}
.rb-music-mobile .Container_c1udi5wp{position:fixed!important;left:0!important;top:0!important;bottom:0!important;width:min(82vw,320px)!important;min-width:0!important;height:100dvh!important;flex:none!important;z-index:9000!important;transform:translateX(-105%);visibility:hidden;pointer-events:none;transition:transform .24s ease,visibility 0s linear .24s;background:#fff;box-shadow:none;}
.rb-music-mobile.rb-music-drawer-open .Container_c1udi5wp{transform:translateX(0);visibility:visible;pointer-events:auto;transition:transform .24s ease,visibility 0s;box-shadow:8px 0 24px #0003;}
.rb-music-mobile .ContentContainer_c5xapan,.rb-music-mobile .BackContainer_b19x2gto{width:100%!important;}
.rb-music-mobile .BackContainer_b19x2gto{justify-content:flex-start!important;padding-left:20px;box-sizing:border-box;}
.rb-music-mobile .LeftScrollContainer_lriil8u{width:100%!important;}
.rb-music-mobile .Container_c1udi5wp .rb-sidebar-hide{display:none!important;}
.rb-music-mobile .RightContainer_r8p6pvv{width:100%!important;min-width:0!important;flex:1 1 0!important;margin:0!important;}
.rb-music-mobile .Container_cv1ujvp{height:64px!important;min-height:64px!important;flex:none!important;padding:0!important;}
.rb-music-mobile .HeaderRow_h1n2o69r{display:flex!important;align-items:center!important;gap:8px!important;grid-template-columns:none!important;width:calc(100% - 24px)!important;min-width:0!important;margin:0 12px!important;}
.rb-music-mobile .HeaderRow_h1n2o69r>.HistoryIconBackground_h1s4i8hh,.rb-music-mobile .HeaderRow_h1n2o69r>div.draggable,.rb-music-mobile .HeaderRow_h1n2o69r>.IconBar_i1ueu1yn{display:none!important;}
.rb-music-mobile .HeaderRow_h1n2o69r>div:empty{display:none!important;}
.rb-music-mobile .DomWarpper_d1sk21qw,.rb-music-mobile .SearchWrapper_s14tpfr6{min-width:0!important;width:100%!important;max-width:none!important;flex:1 1 0!important;}
.rb-music-mobile .SearchWrapper_s14tpfr6 .cmd-input-wrapper{width:100%!important;box-sizing:border-box!important;}
.rb-music-mobile .SearchWrapper_s14tpfr6 input{min-width:0!important;width:100%!important;font-size:15px!important;}
.rb-music-mobile .Bar_b2jgnke{flex:none!important;width:32px!important;min-width:32px!important;justify-content:center!important;gap:0!important;}
.rb-music-mobile .Bar_b2jgnke>.Row_r1jtm31i,.rb-music-mobile .Bar_b2jgnke>.Content_c1r4hsv0,.rb-music-mobile .Bar_b2jgnke>.cmd-icon{display:none!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah{min-width:0!important;width:100%!important;overflow-x:hidden!important;padding:0!important;box-sizing:border-box!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah>.KeepAliveWrapper_kut5be8,.rb-music-mobile .AppScrollContainer_azgs3ah>main{min-width:0!important;width:100%!important;margin:0!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .KeepAliveWrapper_kut5be8,.rb-music-mobile .AppScrollContainer_azgs3ah .ka-wrapper,.rb-music-mobile .AppScrollContainer_azgs3ah .ka-content{min-width:0!important;width:100%!important;max-width:100%!important;margin:0!important;padding:0!important;box-sizing:border-box!important;}
.rb-music-mobile main.Container_c5lfgwg:not(.remove-scroll){width:100%!important;max-width:none!important;min-width:0!important;margin:0!important;padding:12px 16px 90px!important;box-sizing:border-box!important;}
.rb-music-mobile .recent-listen-context{width:100%!important;min-width:0!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:none!important;grid-auto-rows:max-content!important;gap:22px!important;}
.rb-music-mobile .recent-listen-context>div{width:100%!important;min-width:0!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:none!important;grid-auto-rows:max-content!important;row-gap:12px!important;margin:0!important;}
.rb-music-mobile .exposure-bar{min-width:0!important;max-width:100%!important;}
.rb-music-mobile .recent-listen-context>div>.exposure-bar{height:auto!important;min-height:0!important;padding:0!important;margin:0 0 14px!important;align-items:center!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp{width:100%!important;min-width:0!important;grid-template-columns:minmax(0,1fr)!important;padding-bottom:0!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp>.arrow-button{display:none!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp>.content{width:100%!important;overflow-x:auto!important;overflow-y:hidden!important;overscroll-behavior-x:contain;scrollbar-width:none;touch-action:pan-x pan-y;padding-bottom:0!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp>.content::-webkit-scrollbar{display:none;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp .rail-wrapper{display:flex!important;align-items:flex-start!important;gap:12px!important;width:max-content!important;min-width:100%!important;grid-template-columns:none!important;transform:none!important;transition:none!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp .item-main,.rb-music-mobile .GridCarouselWrapper_gcvcmmp .item-sub{display:contents!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp .item-main>*,.rb-music-mobile .GridCarouselWrapper_gcvcmmp .item-sub>*{flex:0 0 144px!important;min-width:0!important;width:144px!important;grid-column:auto!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .item-main>*,.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .item-sub>*{flex-basis:260px!important;width:260px!important;}
.rb-music-mobile .bannerWrapperCls_b10ae4x0 .BannerWrapper_b1rdt4pq{width:100%!important;}
.rb-music-mobile .recent-listen-context .GridCarouselWrapper_gcvcmmp.shorten .bannerWrapperCls_b10ae4x0{display:none!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y{height:130px!important;min-height:0!important;padding:10px!important;box-sizing:border-box!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .card-container{height:72px!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .card-container img{max-height:48px!important;}
.rb-music-mobile .rb-local-rail{display:flex!important;gap:12px!important;width:100%!important;overflow-x:auto!important;overflow-y:hidden!important;overscroll-behavior-x:contain;scrollbar-width:none;touch-action:pan-x pan-y;}
.rb-music-mobile .rb-local-rail>*{flex:0 0 280px!important;min-width:0!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp .item-main>.rb-meta-column,.rb-music-mobile .GridCarouselWrapper_gcvcmmp .item-sub>.rb-meta-column{flex:0 0 280px!important;width:280px!important;min-width:0!important;}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp .rb-meta-shell,.rb-music-mobile .GridCarouselWrapper_gcvcmmp .rb-meta-row{width:100%!important;max-width:100%!important;min-width:0!important;box-sizing:border-box!important;}
.rb-music-mobile .rb-meta-row .cmd-metacard-content{flex:1 1 0!important;width:0!important;min-width:0!important;max-width:100%!important;overflow:hidden!important;}
.rb-music-mobile .rb-meta-row .cmd-metacard-content>.header{display:block!important;width:100%!important;max-width:100%!important;min-width:0!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;}
.rb-music-mobile .rb-meta-row .cmd-metacard-content>p.header{display:-webkit-box!important;-webkit-box-orient:vertical!important;-webkit-line-clamp:2!important;line-clamp:2;white-space:normal!important;overflow-wrap:anywhere;line-height:20px!important;max-height:40px!important;}
.rb-music-mobile .rb-meta-row .cmd-metacard-content .footer{display:flex!important;min-width:0!important;max-width:100%!important;overflow:hidden!important;}
.rb-music-mobile .rb-meta-row .cmd-metacard-content .artist{flex:1 1 0!important;min-width:0!important;width:0!important;max-width:100%!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;}
.rb-music-mobile .rb-meta-row .cmd-metacard-content .footer>.cmd-typography{min-width:0!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;}
.rb-music-mobile .exposure-bar>.content{min-width:0!important;flex:1 1 0!important;}
.rb-music-mobile .exposure-bar h2{min-width:0!important;max-width:100%!important;overflow:hidden!important;}
.rb-music-mobile .exposure-bar h2 p{display:block!important;min-width:0!important;max-width:100%!important;overflow:hidden!important;white-space:nowrap!important;text-overflow:ellipsis!important;}
#rb-music-menu,#rb-music-close{border:0;background:transparent;color:inherit;display:flex;align-items:center;justify-content:center;cursor:pointer;padding:0;border-radius:8px;touch-action:manipulation;}
#rb-music-menu{width:40px;height:40px;flex:none;}
#rb-music-close{position:absolute;right:12px;top:18px;width:36px;height:36px;z-index:2;}
#rb-music-menu:hover,#rb-music-close:hover{background:#8882;}
#rb-music-mask{position:fixed;inset:0;z-index:8999;background:#0005;opacity:0;visibility:hidden;pointer-events:none;transition:opacity .24s ease,visibility 0s linear .24s;}
.rb-music-drawer-open #rb-music-mask{opacity:1;visibility:visible;pointer-events:auto;transition:opacity .24s ease,visibility 0s;}
.rb-music-mobile footer.Container_col456s{width:100%!important;max-width:100%!important;min-width:0!important;box-sizing:border-box!important;flex-shrink:0!important;}
.rb-music-mobile footer.Container_col456s>*{min-width:0!important;max-width:100%!important;box-sizing:border-box!important;}
.rb-music-mobile footer.Container_col456s:has(.DefaultBarWrapper_d1fdcdsp),.rb-music-mobile footer.Container_col456s:has(.DefaultBarWrapper_d1fdcdsp)>div{height:132px!important;}
.rb-music-mobile .AppEle_a1llnfj4:has(footer .DefaultBarWrapper_d1fdcdsp)>.RightContainer_r8p6pvv{height:calc(100dvh - 132px)!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp{display:grid!important;grid-template-columns:56px minmax(0,1fr) 70px!important;grid-template-rows:44px 24px 40px!important;gap:4px 8px!important;width:100%!important;height:132px!important;padding:8px 10px!important;min-width:0!important;box-sizing:border-box!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp>.side:not(.right-side),.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .left,.rb-music-mobile .DefaultBarWrapper_d1fdcdsp>.middle{display:contents!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .miniVinylWrapper{grid-column:1!important;grid-row:1 / span 3!important;align-self:center!important;justify-self:center!important;width:56px!important;height:56px!important;min-width:0!important;margin:0!important;left:auto!important;top:auto!important;position:relative!important;transform:none!important;box-sizing:border-box!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .miniVinylWrapper img{max-width:100%!important;max-height:100%!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .songPlayInfo_s6acgr1{grid-column:2 / span 2!important;grid-row:1!important;width:100%!important;height:44px!important;min-width:0!important;max-width:100%!important;margin:0!important;row-gap:0!important;overflow:hidden!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .songPlayInfo_s6acgr1>.cmd-space{min-width:0!important;max-width:100%!important;height:22px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .title-container{min-width:0!important;max-width:100%!important;overflow:hidden!important;white-space:nowrap!important;text-overflow:ellipsis!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .SpaceContainer_s8o78dm{grid-column:2 / span 2!important;grid-row:2!important;width:100%!important;min-width:0!important;height:24px!important;gap:6px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .SpaceContainer_s8o78dm .StyledSliderContainer_smigcsb{flex:1 1 0!important;width:0!important;min-width:0!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .miniBarTimeTextStyle_m1tgepvu{flex:none!important;font-size:11px!important;white-space:nowrap!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns{grid-column:2!important;grid-row:3!important;width:100%!important;min-width:0!important;height:40px!important;gap:8px!important;justify-content:center!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns>*{flex:none!important;width:32px!important;height:32px!important;margin:0!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns>.play-pause-btn{width:40px!important;height:40px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp>.right-side{grid-column:3!important;grid-row:3!important;min-width:0!important;width:70px!important;height:40px!important;margin:0!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .right{width:70px!important;height:40px!important;gap:6px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .right>*{flex:none!important;width:32px!important;margin:0!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .right button{width:32px!important;height:32px!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .thead,.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .td-num,.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .td-album,.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .td-duration{display:none!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm{width:100%!important;max-width:100%!important;min-width:0!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .tr{width:calc(100vw - 32px)!important;max-width:calc(100vw - 32px)!important;min-width:0!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTitleCell_s1orgcyh,.rb-music-mobile .AppScrollContainer_azgs3ah .td-title .cmd-metacard-table{min-width:0!important;width:100%!important;max-width:100%!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .td-title .cmd-metacard-content{min-width:0!important;flex:1 1 0!important;width:0!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .td-title .cmd-metacard-content .ellipsis{min-width:0!important;max-width:100%!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .td-title .cmd-metacard-content .sub-title{min-width:0!important;max-width:100%!important;overflow:hidden!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .td-title{flex:1 1 0!important;min-width:0!important;width:0!important;}
.rb-music-mobile .AppScrollContainer_azgs3ah .StyledTableViewer_sv4xczm .td-oper{flex:0 0 40px!important;width:40px!important;}
.rb-music-mobile .rb-song-detail{display:grid!important;grid-template-columns:var(--rb-cover-size,145.5px) minmax(0,1fr)!important;grid-template-rows:auto auto!important;gap:8px 12px!important;min-width:0!important;width:100%!important;}
.rb-music-mobile .rb-song-detail>.StyledCommon_s1pv4hmr{display:grid!important;grid-column:1 / span 2!important;grid-row:1!important;grid-template-columns:var(--rb-cover-size,145.5px) minmax(0,1fr)!important;grid-template-rows:auto auto 40px!important;gap:8px 12px!important;min-width:0!important;padding:0!important;margin:0!important;}
.rb-music-mobile .rb-song-detail .StyledCommon_s1pv4hmr>.info,.rb-music-mobile .rb-song-detail>.TabsCss_tquhgfk,.rb-music-mobile .rb-song-detail>.TabsCss_tquhgfk>.cmd-tabs-bar{display:contents!important;}
.rb-music-mobile .rb-song-detail .StyledCover_szv5e0{grid-column:1!important;grid-row:1 / span 2!important;width:var(--rb-cover-size,145.5px)!important;height:var(--rb-cover-size,145.5px)!important;min-width:0!important;}
.rb-music-mobile .rb-song-detail .StyledCover_szv5e0>.img{width:100%!important;height:100%!important;}
.rb-music-mobile .rb-song-detail .StyledStickyOper_svzrh3b{display:none!important;}
.rb-music-mobile .rb-song-detail .StyledCommon_s1pv4hmr .info>.title{grid-column:2!important;grid-row:1!important;min-width:0!important;width:auto!important;max-width:100%!important;margin:0!important;overflow:hidden!important;display:-webkit-box!important;-webkit-box-orient:vertical!important;-webkit-line-clamp:2!important;overflow-wrap:anywhere;}
.rb-music-mobile .rb-song-detail .StyledCommon_s1pv4hmr .info>.more{grid-column:2!important;grid-row:2!important;min-width:0!important;max-width:100%!important;overflow:hidden!important;}
.rb-music-mobile .rb-song-detail .StyledCommon_s1pv4hmr .info>.btns{grid-column:1!important;grid-row:3!important;min-width:0!important;width:100%!important;display:flex!important;align-items:center!important;gap:8px!important;}
.rb-music-mobile .rb-song-detail>.TabsCss_tquhgfk>.cmd-tabs-bar>.cmd-tabs-tab,.rb-music-mobile .rb-song-detail>.TabsCss_tquhgfk>.cmd-tabs-content{display:none!important;}
.rb-music-mobile .rb-song-detail>.TabsCss_tquhgfk>.cmd-tabs-bar>.cmd-tabs-bar-extra{grid-column:2!important;grid-row:1!important;align-self:end!important;position:static!important;width:100%!important;min-width:0!important;height:40px!important;margin:0!important;display:flex!important;align-items:center!important;}
.rb-music-mobile .rb-song-detail .search-input{width:100%!important;min-width:0!important;box-sizing:border-box!important;}
.rb-music-mobile .rb-song-detail .search-input input{width:100%!important;min-width:0!important;}
.rb-music-mobile .rb-song-detail>.StyledContent_s1be8dop{grid-column:1 / span 2!important;grid-row:2!important;min-width:0!important;width:100%!important;}
.rb-music-mobile .rb-recent-page{width:100%!important;min-width:0!important;max-width:100%!important;}
.rb-music-mobile .btn_pc_recently_play_subtab{width:100%!important;min-width:0!important;max-width:100%!important;}
.rb-music-mobile .btn_pc_recently_play_subtab>.cmd-tabs-bar{display:flex!important;align-items:center!important;width:100%!important;min-width:0!important;max-width:100%!important;box-sizing:border-box!important;}
.rb-music-mobile .btn_pc_recently_play_subtab .cmd-tabs-bar-extra{position:static!important;float:none!important;margin-left:auto!important;flex:none!important;}
.rb-music-mobile .btn_pc_recently_play_subtab .cmd-tabs-bar-extra .cmd-space{display:flex!important;width:auto!important;gap:8px!important;}
.rb-music-mobile .btn_pc_recently_play_subtab .play-all{order:1;white-space:nowrap!important;flex:none!important;}
.rb-music-mobile .btn_pc_recently_play_subtab .rb-recent-hide{display:none!important;}
.rb-music-mobile .rb-recent-page .StyledTableViewer_sv4xczm .td-playTime{flex:0 0 64px!important;width:64px!important;font-size:11px!important;white-space:normal!important;overflow-wrap:anywhere;overflow:hidden!important;}
.rb-music-mobile .rb-recent-page .td-title .title-content{min-width:0!important;max-width:100%!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp{grid-template-columns:repeat(12,minmax(0,1fr))!important;column-gap:4px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .miniVinylWrapper{grid-column:1 / span 2!important;grid-row:1 / span 2!important;min-height:0!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .songPlayInfo_s6acgr1{display:contents!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .songPlayInfo_s6acgr1>.info-wrapper{grid-column:3 / span 10!important;grid-row:1!important;width:100%!important;height:44px!important;align-items:center!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .songPlayInfo_s6acgr1>.cmd-space:not(.info-wrapper){grid-column:1 / span 3!important;grid-row:3!important;width:100%!important;height:40px!important;gap:4px!important;align-items:center!important;justify-content:flex-start!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .songPlayInfo_s6acgr1>.cmd-space:not(.info-wrapper)>button{flex:none!important;width:24px!important;height:32px!important;margin:0!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .SpaceContainer_s8o78dm{grid-column:3 / span 10!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns{grid-column:4 / span 6!important;gap:4px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns>*{width:28px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns>.play-pause-btn{width:40px!important;}
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp>.right-side{grid-column:10 / span 3!important;width:100%!important;justify-content:flex-end!important;}
`;
const own=(tag,id)=>{const n=document.createElement(tag);if(id)n.id=id;nodes.push(n);return n;};
const button=(id,label,svg)=>{const b=own('button',id);b.type='button';b.setAttribute('aria-label',label);b.title=label;b.innerHTML=svg;return b;};
const menu=button('rb-music-menu','展开导航','<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>');
const close=button('rb-music-close','收起导航','<svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>');
const mask=own('div','rb-music-mask');mask.setAttribute('aria-hidden','true');
body.append(mask);side.append(close);document.head.append(style);
const controlsStyle=own('style','rb-music-controls-style');
controlsStyle.textContent=`
.rb-music-mobile .DefaultBarWrapper_d1fdcdsp .btns>#btn_pc_minibar_play{
 flex:0 0 40px!important;width:40px!important;height:40px!important;
 min-width:40px!important;max-width:40px!important;min-height:40px!important;max-height:40px!important;box-sizing:border-box!important;
}`;
document.head.append(controlsStyle);
const expandedStyle=own('style','rb-music-expanded-style');
expandedStyle.textContent=`/* Android-owned addition for the pinned official bundle. Never reparent React nodes,
   change player state, or override the page's open/close/comment transforms.
   Removed with rb-music-mobile when the native window policy selects original layout. */
.rb-music-mobile .VinylPageContainer_v1ep2mqc .page-header {
 height:48px!important;padding:8px 16px!important;box-sizing:border-box!important;
}
.rb-music-mobile .Container_c1m2d5ze .left .close {
 height:40px!important;
}
.rb-music-mobile .VinylPageLayoutContainer_vwagppq {
 display:grid!important;grid-template-columns:minmax(0,1fr)!important;
 /* Classic lyrics target the WINDOW midpoint, not their own midpoint. Keep the
    lyric viewport above that anchor; a desktop-height cover makes it negative. */
 grid-template-rows:minmax(88px,max-content) clamp(100px,20vh,200px) minmax(0,1fr)!important;
 align-items:start!important;gap:8px!important;height:100%!important;min-width:0!important;
 padding:8px 18px 146px!important;box-sizing:border-box!important;
}
.rb-music-mobile .VinylPageLayoutContainer_vwagppq>.side,
.rb-music-mobile .VinylPageLayoutContainer_vwagppq>.center {
 display:none!important;
}
.rb-music-mobile .VinylPageLayoutContainer_vwagppq>.display {
 display:contents!important;
}
.rb-music-mobile .VinylPageLayoutContainer_vwagppq>.vinyl {
 grid-column:1!important;grid-row:2!important;justify-self:center!important;
 width:min(64vw,20vh,200px)!important;height:100%!important;min-width:0!important;
}
.rb-music-mobile .ClassicPageContainer_c1h09pwc .cover-area,
.rb-music-mobile .VinylPageLayoutContainer_vwagppq .VinylCoverContainer_v1gm9f7r {
 inset:0!important;width:100%!important;height:100%!important;
}
.rb-music-mobile .VinylPageLayoutContainer_vwagppq .TrackTitleContainer_t1s72en4 {
 grid-column:1!important;grid-row:1!important;min-width:0!important;
 align-self:start!important;gap:4px!important;
}
/* Use the space beside the real close button; credits then use the full width. */
.rb-music-mobile .TrackTitleContainer_t1s72en4 .one-line,
.rb-music-mobile .TrackTitleContainer_t1s72en4 .two-line {
 margin-left:52px!important;min-height:40px!important;align-items:center!important;
}
.rb-music-mobile .TrackTitleContainer_t1s72en4 .title {
 font-size:20px!important;line-height:26px!important;
}
.rb-music-mobile .TrackTitleContainer_t1s72en4 .info-line {
 display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;
 gap:2px 12px!important;min-width:0!important;
}
.rb-music-mobile .TrackTitleContainer_t1s72en4 .info:nth-child(3) {
 grid-column:1 / -1!important;
}
.rb-music-mobile .TrackTitleContainer_t1s72en4 .info {
 min-width:0!important;width:auto!important;flex:1 1 0!important;
 overflow:hidden!important;text-overflow:ellipsis!important;white-space:nowrap!important;
}
.rb-music-mobile .VinylPageLayoutContainer_vwagppq .TrackDisplayContainer_t11eiop9 {
 grid-column:1!important;grid-row:3!important;height:100%!important;min-height:0!important;
 min-width:0!important;overflow:hidden!important;
}
.rb-music-mobile .TrackDisplayContainer_t11eiop9>.tab-group {
 flex:none!important;max-width:100%!important;white-space:nowrap!important;overflow-x:auto!important;
}
.rb-music-mobile .TrackDisplayContainer_t11eiop9>.area {
 min-height:0!important;min-width:0!important;
}
.rb-music-mobile .ClassicPageContainer_c1h09pwc .scroll-tip {
 display:none!important; /* Desktop wheel hint, not an Android touch control. */
}
.rb-music-mobile .LyricDefaultPageContainer_lnd8jru {
 padding:64px 20px 146px!important;box-sizing:border-box!important;min-width:0!important;
 font-size:10px!important;
}
.rb-music-mobile .LyricDefaultPageContainer_lnd8jru>.track-title,
.rb-music-mobile .LyricDefaultPageContainer_lnd8jru>.artist {
 max-width:100%!important;flex-shrink:0!important;
}
.rb-music-mobile .LyricDisplayInnerContainer_l1o4576w {
 min-width:0!important;min-height:0!important;
}
.rb-music-mobile .LyricDisplayInnerContainer_l1o4576w .lyric {
 min-width:0!important;overscroll-behavior:contain;
}
.rb-music-mobile .LyricDisplayInnerContainer_l1o4576w .lyric .line {
 overflow-wrap:anywhere!important; /* Long Latin lines must not form a second column. */
}
/* The expanded bar is a separate official component, not DefaultBar.
   Its real slider remains above this two-row action grid. */
.rb-music-mobile .VinylPageContainer_v1ep2mqc .page-footer {
 padding-bottom:8px!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu {
 display:grid!important;grid-template-columns:minmax(0,1fr) auto!important;
 grid-template-rows:56px 40px!important;gap:2px 6px!important;
 height:100px!important;min-width:0!important;padding:0 12px!important;box-sizing:border-box!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu>.middle {
 grid-row:1!important;grid-column:1 / -1!important;justify-content:center!important;
 width:100%!important;min-width:0!important;height:56px!important;gap:10px!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu>.middle>* {
 flex:0 0 36px!important;width:36px!important;height:40px!important;margin:0!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu>.middle>#btn_pc_minibar_play {
 flex-basis:44px!important;width:44px!important;min-width:44px!important;max-width:44px!important;
 height:44px!important;min-height:44px!important;max-height:44px!important;box-sizing:border-box!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu>.side:not(.right-side) {
 grid-row:2!important;grid-column:1!important;min-width:0!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .left {
 height:40px!important;gap:6px!important;min-width:0!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .left>button {
 flex:0 0 28px!important;width:28px!important;height:36px!important;margin:0!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .left .time {
 font-size:11px!important;white-space:nowrap!important;flex:none!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .patch {
 display:none!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu>.right-side {
 grid-row:2!important;grid-column:2!important;min-width:0!important;width:auto!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .right {
 height:40px!important;gap:4px!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .right>* {
 flex:none!important;margin:0!important;
}
.rb-music-mobile .VinylPageBarWrapper_v1xybaiu .right button {
 width:28px!important;height:36px!important;
}
@media (max-height:760px) {
 .rb-music-mobile .VinylPageLayoutContainer_vwagppq {
  grid-template-rows:minmax(88px,max-content) minmax(0,1fr)!important;gap:6px!important;padding-top:8px!important;
 }
 .rb-music-mobile .VinylPageLayoutContainer_vwagppq>.vinyl {display:none!important;}
 .rb-music-mobile .VinylPageLayoutContainer_vwagppq .TrackDisplayContainer_t11eiop9 {grid-row:2!important;}
}
`;
document.head.append(expandedStyle);
const homeStyle=own('style','rb-music-home-style');
homeStyle.textContent=`/* Android-owned homepage refinement; keep the approved reference immutable.
   The official rail contains .cards pages: preserve its paging/transform/events.
   Hide entries2+3 WITHIN a four-entry page, not the pages themselves. */
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .rail-wrapper {
 align-items:stretch!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y {
 height:auto!important;min-height:0!important;align-self:stretch!important;
 padding:10px!important;box-sizing:border-box!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y>.card-container {
 flex:1 1 auto!important;width:100%!important;min-width:0!important;height:auto!important;
 margin:0!important;padding:10px 0 0!important;box-sizing:border-box!important;
 align-items:center!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .card-rail {
 height:auto!important;min-width:0!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .card-rail>.cards {
 grid-template-columns:repeat(2,minmax(0,1fr))!important;
 gap:12px!important;width:100%!important;height:auto!important;min-width:0!important;
 flex:0 0 100%!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .cards:has(>:nth-child(4))>:not(:nth-child(1)):not(:nth-child(4)) {
 display:none!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .cards>.RecentListenCardContainer_r1b6smzd {
 min-width:0!important;height:0!important;padding-top:100%!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .cards>.RecentListenCardContainer_r1b6smzd>img.bg {
 width:100%!important;height:100%!important;max-height:none!important;
 object-fit:cover!important;
}
.rb-music-mobile .GridCarouselWrapper_gcvcmmp.shorten .RecentListenContainer_r1w1uh6y .cards>.RecentListenCardContainer_r1b6smzd>img.icon {
 width:60%!important;height:auto!important;max-height:none!important;
}
`;
document.head.append(homeStyle);document.documentElement.classList.add('rb-music-mobile');
let viewport=document.querySelector('meta[name="viewport"]'),createdViewport=false,oldViewport;
if(!viewport){viewport=own('meta','rb-music-mobile-viewport');viewport.name='viewport';document.head.append(viewport);createdViewport=true;}else oldViewport=viewport.getAttribute('content');
viewport.content='width=device-width,initial-scale=1';
function listen(node,name,fn,options){node.addEventListener(name,fn,options);bindings.push([node,name,fn,options]);}
function toggle(value){open=!!value;document.documentElement.classList.toggle('rb-music-drawer-open',open);side.inert=!open;menu.setAttribute('aria-expanded',String(open));if(!open&&side.contains(document.activeElement))menu.focus({preventScroll:true});}
listen(menu,'click',e=>{e.preventDefault();e.stopPropagation();toggle(!open);});listen(close,'click',e=>{e.preventDefault();e.stopPropagation();toggle(false);});listen(mask,'click',()=>toggle(false));
function route(){return window.__rbNeteaseProviderV1?.route()||location.hash||location.pathname;}
const markerClasses=['rb-local-rail','rb-recent-hide','rb-recent-page','rb-sidebar-hide','rb-meta-column','rb-meta-row','rb-meta-shell','rb-song-detail'];
function restoreCover(n,v){if(v.value)n.style.setProperty('--rb-cover-size',v.value,v.priority);else n.style.removeProperty('--rb-cover-size');}
function prune(){
 for(const n of marked)if(!n.isConnected){n.classList.remove(...markerClasses);marked.delete(n);}
 for(const [n,v] of coverStyles)if(!n.isConnected){restoreCover(n,v);coverStyles.delete(n);}
}
function attach(){if(disposed)return;prune();const h=document.querySelector('.HeaderRow_h1n2o69r');if(h&&!menu.isConnected)h.prepend(menu);if(side.isConnected&&!close.isConnected)side.append(close);
const nextRoute=route();if(lastRoute&&lastRoute!==nextRoute)toggle(false);lastRoute=nextRoute;
for(const label of side.querySelectorAll('.title'))if(['我的博客','我的播客','关注','漫游','播客'].includes(label.textContent.trim())){const item=label.closest('.ItemContainer_ijv59hq');if(item){item.classList.add('rb-sidebar-hide');marked.add(item);}}
for(const tabs of document.querySelectorAll('.btn_pc_recently_play_subtab')){
const page=tabs.parentElement;page.classList.add('rb-recent-page');marked.add(page);
for(const tab of tabs.querySelectorAll('.cmd-tabs-tab'))if(Array.from(tab.querySelectorAll('span')).some(x=>['播客','有声书','歌单','专辑'].includes(x.textContent.trim()))){tab.classList.add('rb-recent-hide');marked.add(tab);}
for(const b of tabs.querySelectorAll('.cmd-tabs-bar-extra button'))if(b.textContent.trim()==='收藏全部'){b.classList.add('rb-recent-hide');marked.add(b);}
}
// Keep the original search node and React ownership. CSS grid puts it on the action row.
for(const header of document.querySelectorAll('.StyledCommon_s1pv4hmr')){const group=header.parentElement,cover=header.querySelector('.StyledCover_szv5e0');if(!cover||!group.querySelector(':scope>.TabsCss_tquhgfk'))continue;if(!coverStyles.has(group)){const width=cover.getBoundingClientRect().width;if(width<=0)continue;coverStyles.set(group,{value:group.style.getPropertyValue('--rb-cover-size'),priority:group.style.getPropertyPriority('--rb-cover-size')});group.style.setProperty('--rb-cover-size',String(width*.75)+'px');}group.classList.add('rb-song-detail');marked.add(group);}
// Original horizontal metadata cards must shrink with their column, not keep desktop widths.
for(const card of document.querySelectorAll('.GridCarouselWrapper_gcvcmmp .MetaCardWrapper_m5gg9e7')){const s=getComputedStyle(card);if(s.display!=='flex'||s.flexDirection!=='row')continue;const column=card.closest('.item-main>*,.item-sub>*');if(!column)continue;card.classList.add('rb-meta-row');marked.add(card);column.classList.add('rb-meta-column');marked.add(column);for(let p=card.parentElement;p&&p!==column;p=p.parentElement){p.classList.add('rb-meta-shell');marked.add(p);}}
// Reflow only the homepage's original direct two-column modules. No nodes are cloned or reparented.
for(const root of document.querySelectorAll('main.Container_c5lfgwg .recent-listen-context'))for(const block of root.children)for(const candidate of block.children){if(candidate.className||candidate.children.length<2||candidate.children.length>4)continue;const s=getComputedStyle(candidate);if(s.display==='grid'&&s.gridTemplateColumns.split(' ').length>1){candidate.classList.add('rb-local-rail');marked.add(candidate);}}
}
function handleBack(){if(open){toggle(false);return true;}return window.__rbNeteaseProviderV1?.back()==='handled';}

let frame=0;const observer=new MutationObserver(()=>{if(!disposed&&!frame)frame=requestAnimationFrame(()=>{frame=0;attach();});});observer.observe(document.getElementById('root'),{childList:true,subtree:true});
attach();toggle(false);const timer=setInterval(attach,500);
// Capture only the fixed official state/navigation facade already loaded by this page.

function status(){const rect=main.getBoundingClientRect();return {ok:true,open,viewportWidth:innerWidth,mainX:Math.round(rect.x),mainWidth:Math.round(rect.width),drawerWidth:Math.round(side.getBoundingClientRect().width),carouselCount:document.querySelectorAll('.GridCarouselWrapper_gcvcmmp>.content').length,providerAvailable:!!window.__rbNeteaseProviderV1,originalPlayerRetained:true,persistent:false};}
function cleanup(){
 if(disposed)return;disposed=true;observer.disconnect();clearInterval(timer);if(frame)cancelAnimationFrame(frame);frame=0;
 for(const b of bindings)b[0].removeEventListener(b[1],b[2],b[3]);bindings.length=0;
 for(const n of nodes)n.remove();nodes.length=0;style.remove();
 for(const n of marked)n.classList.remove(...markerClasses);marked.clear();
 for(const [n,v] of coverStyles)restoreCover(n,v);coverStyles.clear();
 if(!createdViewport){if(oldViewport===null)viewport.removeAttribute('content');else viewport.setAttribute('content',oldViewport);}
 document.documentElement.classList.remove('rb-music-mobile','rb-music-drawer-open');side.inert=oldInert;
 delete window.__rbNeteasePortraitLayoutV1;
}

function backResult(){
 if(open){toggle(false);return 'handled';}
 const result=window.__rbNeteaseProviderV1?.back();
 return ['handled','root','layer'].includes(result)?result:'layer';
}
window.__rbNeteasePortraitLayoutV1={toggle,status,cleanup,handleBack,backResult};return status();
})();
}finally{
    side.getBoundingClientRect();
    if(transition)side.style.setProperty('transition',transition,priority);else side.style.removeProperty('transition');
  }
};
function attempt(){
  timer=0;
  if(disposed||!portrait||window.__rbNeteasePortraitLayoutV1)return;
  const knownBundle=Array.from(document.scripts||[]).some(s=>{try{const u=new URL(s.src);return u.protocol==='https:'&&u.hostname==='s5.music.126.net'&&u.pathname.endsWith('/hybrid/app.4c9df986.js');}catch{return false;}});
  if(knownBundle&&document.body&&document.getElementById('root')&&document.querySelector('.Container_c1udi5wp')&&document.querySelector('.RightContainer_r8p6pvv')){install();return;}
  if(++attempts<120)timer=setTimeout(attempt,250);
}
function setPortrait(value){
  if(disposed)return;
  portrait=value===true;clearTimeout(timer);timer=0;
  if(!portrait){window.__rbNeteasePortraitLayoutV1?.cleanup();return;}
  attempts=0;attempt();
}
function ready(){return !disposed&&(!portrait||!!window.__rbNeteasePortraitLayoutV1);}
function prepare(){
  if(disposed||!portrait||window.__rbNeteasePortraitLayoutV1||attempts>=120)return;
  clearTimeout(timer);timer=0;attempt();
}
function cleanup(){disposed=true;clearTimeout(timer);window.__rbNeteasePortraitLayoutV1?.cleanup();delete window.__rbMusicLayoutHostV1;}
window.__rbMusicLayoutHostV1=Object.freeze({setPortrait,ready,prepare,cleanup,back:()=>window.__rbNeteasePortraitLayoutV1?.backResult()||window.__rbNeteaseProviderV1?.back()||'unavailable'});
setPortrait(portrait);
})();
